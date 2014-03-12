package test

import scala.language.higherKinds

import scalax.collection.GraphPredef._
import scalax.collection.GraphEdge._
import scalax.collection.Graph

import org.scalatest.Suite
import org.scalatest.matchers.ShouldMatchers

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class TraversingTest
    extends Suite
       with ShouldMatchers {

  import scalax.collection.edge.{WDiEdge, WUnDiEdge}
  import scalax.collection.edge.Implicits._

  def validatePath[N, E[X] <: EdgeLikeIn[X]](p: Graph[N,E]#Path,
                                             sample: Traversable[Param[N,E]]): Unit = {
    def toN(p: Param[N,E]): N = p match {
      case OuterNode(n) => n.value
      case _ => throw new IllegalArgumentException
    }
    p.toList == sample ||
    p.isValid && p.startNode == toN(sample.head) &&
                 p.endNode   == toN(sample.last) should be (true)
  }
         
  def test_forAResult {
    val g = Graph(1~2 % 4, 2~3 % 2, 1~>3 % 5, 1~5  % 3,
                  3~5 % 2, 3~4 % 1, 4~>4 % 1, 4~>5 % 0)
    def n(outer: Int): g.NodeT = g get outer
    
    n(1) findSuccessor (_.outDegree >  3)              should be (None) 
    n(1) findSuccessor (_.outDegree >= 3)              should be (Some(3)) 
    n(4) findSuccessor (_.edges forall (_.undirected)) should be (Some(2))
    n(4) isPredecessorOf n(1)                          should be (true)
                                 validatePath[Int,WUnDiEdge]((
    n(1) pathTo n(4)
                                 ).get, List(1, 1~>3 %5, 3, 3~4 %1, 4))
                                 validatePath[Int,WUnDiEdge]((
    n(1) pathUntil (_.outDegree >= 3)
                                 ).get, List(1, 1~>3 %5, 3))
    val spO = n(3) shortestPathTo n(1)
    val sp = spO.get
                                 validatePath[Int,WUnDiEdge](sp,
                                 List(3, 3~4 %1, 4, 4~>5 %0, 5, 1~5 %3, 1))
    sp.nodes                     .toList should be (List(3, 4, 5, 1))
    sp.weight                    should be (4)
    
    val pO1 = n(4).withSubgraph(nodes = _ < 4) pathTo n(2)
                                 validatePath[Int,WUnDiEdge](pO1.get,
                                 List(4, 3~4 %1, 3, 2~3 %2, 2))
    pO1.map(_.nodes)             .get.toList should be (List(4, 3, 2)) 
    
    val pO2 = n(4).withSubgraph(edges = _.weight != 2) pathTo n(2)
                                 validatePath[Int,WUnDiEdge](pO2.get,
                                 List(4, 4~>5 %0, 5, 1~5 %3, 1, 1~2 %4, 2))
    pO2.map(_.nodes)             .get.toList should be (List(4, 5, 1, 2)) 
  }

  def test_CycleDetecting {
    val g = Graph(1~>2, 1~>3, 2~>3, 3~>4, 4~>2)
    val fc1 = g.findCycle
                                 fc1.get.sameElements(List(
                                 2, 2~>3, 3, 3~>4, 4, 4~>2, 2)) should be (true)
    val fc2 = (g get 4).findCycle
                                 fc2.get.sameElements(List(
                                 4, 4~>2, 2, 2~>3, 3, 3~>4, 4)) should be (true)
    for (c1 <- fc1; c2 <- fc2) yield c1 == c2          should be (false)
    for (c1 <- fc1; c2 <- fc2) yield c1 sameAs c2      should be (true)
  }
  
  def test_Ordering {
    val root = 1
    val g = Graph(root~>4 % 2, root~>2 % 5, root~>3 % 4,
                     3~>6 % 4,    3~>5 % 5,    3~>7 % 2)
    
    def edgeOrdering = g.EdgeOrdering(g.Edge.WeightOrdering.reverse.compare)
    val traverser = (g get root).outerNodeTraverser.withOrdering(edgeOrdering)
     
    traverser.toList             should be (List(1,2,3,4,5,6,7))
  }
  
  def test_Traversers {
    val g = Graph(1~>2 % 1, 1~>3 % 2, 2~>3 % 3, 3~>4 % 1)
    val n1 = g get 1
    
    n1.outerNodeTraverser.sum                 should be (10)
    g.outerNodeTraverser(n1).sum              should be (10)
    n1.outerNodeTraverser.withMaxDepth(1).sum should be (6)
    
    n1.innerEdgeTraverser.map(_.weight).sum   should be (7)
    
    n1.innerElemTraverser.filter(_ match {
      case g.InnerNode(n) => n.degree > 1
      case g.InnerEdge(e) => e.weight > 1
    })                           .map[OuterElem[Int,WDiEdge],
                                      Traversable[OuterElem[Int,WDiEdge]]]((_: g.InnerElem) match {
                                   case g.InnerNode(n) => n.value
                                   case g.InnerEdge(e) => e.toOuter
                                 }).toSet should be (Set[OuterElem[Int,WDiEdge]](
                                 1, 2, 3, 1~>3 % 2, 2~>3 % 3))
  }
  
  def test_DownUp {
    import scala.collection.mutable.ArrayBuffer

    val root = "A"
    val g = Graph(root~>"B1", root~>"B2")
    val innerRoot = g get root
    val result = (ArrayBuffer.empty[String] /: innerRoot.innerNodeDownUpTraverser) {
        (buf, param) => param match {
          case (down, node) => 
            if (down) buf += (if (node eq innerRoot) "(" else "[") += node.toString
            else      buf += (if (node eq innerRoot) ")" else "]")
        }
    }
    ("" /: result)(_+_)          should be ("(A[B1][B2])")
  }

  def test_Extended {
    val g = Graph(1 ~> 2, 1 ~> 3, 2 ~> 3, 3 ~> 4, 4 ~> 2)

    import g.ExtendedNodeVisitor
    import scalax.collection.GraphTraversal._
    import scalax.collection.GraphTraversalImpl._

    var info = List.empty[String]
    (g get 1).innerNodeTraverser.withKind(DepthFirst).foreach {
      ExtendedNodeVisitor((node, count, depth, informer) => {
        info :+= s"$node at $depth"
        VisitorReturn.Continue
      })
    }
    info should be (List("1 at 0", "2 at 1", "3 at 1", "4 at 2"))    
  }
  
  def test_Combined {
    val g = Graph(1~>2, 1~>3, 2~>3, 3~>4, 4~>2)
    var center: Option[g.NodeT] = None
                                 val c =
   (g get 4).findCycle( n =>
     center = center match {
       case s @ Some(c) => if (n.degree > c.degree) Some(n) else s
       case None        => Some(n)
     }
   )
                                 c.get.sameElements(List(
                                 2, 2~>3, 3, 3~>4, 4, 4~>2, 2)) should be (true)
                                 center.get should be (2)
 }

  def test_Components {
    def someEdges(i: Int) =
      List((i) ~> (i + 1), (i) ~> (i + 2), (i + 1) ~> (i + 2))

    val disconnected = Graph.from(edges = someEdges(1) ++ someEdges(5))
    val sums =
      for (c <- disconnected.componentTraverser())
        yield c.nodes.head.outerNodeTraverser.sum
                                 sums should be (List(6, 18))
  }
}