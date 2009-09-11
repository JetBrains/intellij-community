package com.intellij.cyclicDependencies;

import com.intellij.util.graph.Graph;

import java.util.*;

/**
 * User: anna
 * Date: Feb 13, 2005
 */
public class CyclicGraphUtil {
  private CyclicGraphUtil() {
  }

  public static <Node> Set<List<Node>> getNodeCycles(final Graph<Node> graph, final Node node){
    Set<List<Node>> result = new HashSet<List<Node>>();


    final Graph<Node> graphWithoutNode = new Graph<Node>() {
      public Collection<Node> getNodes() {
        final Collection<Node> nodes = graph.getNodes();
        nodes.remove(node);
        return nodes;
      }

      public Iterator<Node> getIn(final Node n) {
        final HashSet<Node> nodes = new HashSet<Node>();
        final Iterator<Node> in = graph.getIn(n);
        for(;in.hasNext();){
          nodes.add(in.next());
        }
        nodes.remove(node);
        return nodes.iterator();
      }

      public Iterator<Node> getOut(final Node n) {
        final HashSet<Node> nodes = new HashSet<Node>();
        final Iterator<Node> out = graph.getOut(n);
        for(;out.hasNext();){
          nodes.add(out.next());
        }
        nodes.remove(node);
        return nodes.iterator();
      }

    };

    final HashSet<Node> inNodes = new HashSet<Node>();
    final Iterator<Node> in = graph.getIn(node);
    for(;in.hasNext();){
      inNodes.add(in.next());
    }
    final HashSet<Node> outNodes = new HashSet<Node>();
    final Iterator<Node> out = graph.getOut(node);
    for(;out.hasNext();){
      outNodes.add(out.next());
    }

    final HashSet<Node> retainNodes = new HashSet<Node>(inNodes);
    retainNodes.retainAll(outNodes);
    for (Node node1 : retainNodes) {
      ArrayList<Node> oneNodeCycle = new ArrayList<Node>();
      oneNodeCycle.add(node1);
      oneNodeCycle.add(node);
      result.add(oneNodeCycle);
    }

    inNodes.removeAll(retainNodes);
    outNodes.removeAll(retainNodes);

    final ShortestPathUtil<Node> algorithm = new ShortestPathUtil<Node>(graphWithoutNode);

    for (Node fromNode : outNodes) {
      for (Node toNode : inNodes) {
        final List<Node> shortestPath = algorithm.getShortestPath(toNode, fromNode);
        if (shortestPath != null) {
          ArrayList<Node> path = new ArrayList<Node>();
          path.addAll(shortestPath);
          path.add(node);
          result.add(path);
        }
      }
    }
    return result;
  }
}
