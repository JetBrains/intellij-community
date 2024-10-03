// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.graph.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;


/**
 * Find all simple cycles of a directed graph using the Johnson's algorithm.
 * The implementation is copied and adapted from JGraphT one.
 *
 * @see <a href="https://github.com/jgrapht/jgrapht/blob/master/jgrapht-core/src/main/java/org/jgrapht/alg/cycle/JohnsonSimpleCycles.java">JGraphT implementation</a>
 */
@ApiStatus.Internal
public class SimpleCyclesIterator<Node> {
  // The graph.
  private Graph<Node> myGraph;

  // The main state of the algorithm.
  private @NotNull Consumer<? super List<Node>> myCycleConsumer = null;
  private Node[] myIToV = null;
  private Map<Node, Integer> myVToI = null;
  private Set<Node> myBlocked = null;
  private Map<Node, Set<Node>> myBSets = null;
  private ArrayDeque<Node> myStack = null;

  // The state of the embedded Tarjan SCC algorithm.
  private List<Set<Node>> myFoundSCCs = null;
  private int myIndex = 0;
  private Map<Node, Integer> myVIndex = null;
  private Map<Node, Integer> myVLowlink = null;
  private ArrayDeque<Node> myPath = null;
  private Set<Node> myPathSet = null;

  /**
   * Create a simple cycle finder for the specified graph.
   *
   * @param graph - the DirectedGraph in which to find cycles.
   * @throws IllegalArgumentException if the graph argument is <code>
   *                                  null</code>.
   */
  public SimpleCyclesIterator(@NotNull Graph<Node> graph) {
    myGraph = graph;
  }

  public void iterateSimpleCycles(@NotNull Consumer<? super List<Node>> consumer) {
    if (myGraph == null) {
      throw new IllegalArgumentException("Null graph.");
    }
    initState(consumer);

    int startIndex = 0;
    int size = myGraph.getNodes().size();
    while (startIndex < size) {
      Pair<Graph<Node>, Integer> minSCCGResult = findMinSCSG(startIndex);
      if (minSCCGResult != null) {
        startIndex = minSCCGResult.getSecond();
        Graph<Node> scg = minSCCGResult.getFirst();
        Node startV = toV(startIndex);

        //for (E e : scg.outgoingEdgesOf(startV)) {
        //  Node Node = myGraph.getEdgeTarget(e);
        scg.getOut(startV).forEachRemaining(Node -> {
          myBlocked.remove(Node);
          getBSet(Node).clear();
        });
        findCyclesInSCG(startIndex, startIndex, scg);
        startIndex++;
      }
      else {
        break;
      }
    }

    clearState();
  }

  private Pair<Graph<Node>, Integer> findMinSCSG(int startIndex) {
    /*
     * Per Johnson : "adjacency structure of strong component $K$ with least vertex in subgraph
     * of $G$ induced by $(s, s + 1, n)$". Or in contemporary terms: the strongly connected
     * component of the subgraph induced by $(v_1, \dotso ,v_n)$ which contains the minimum
     * (among those SCCs) vertex index. We return that index together with the graph.
     */
    initMinSCGState();

    List<Set<Node>> foundSCCs = findSCCS(startIndex);

    // find the SCC with the minimum index
    int minIndexFound = Integer.MAX_VALUE;
    Set<Node> minSCC = null;
    for (Set<Node> scc : foundSCCs) {
      for (Node Node : scc) {
        int t = toI(Node);
        if (t < minIndexFound) {
          minIndexFound = t;
          minSCC = scc;
        }
      }
    }
    if (minSCC == null) {
      return null;
    }

    // build a graph for the SCC found
    Map<Node, Set<Node>> resultGraph = new HashMap<>();

    for (Node Node : minSCC) {
      resultGraph.putIfAbsent(Node, new HashSet<>());
    }
    for (Node Node : minSCC) {
      for (Node w : minSCC) {
        if (containsEdge(myGraph, Node, w)) {
          resultGraph.get(Node).add(w);
        }
      }
    }

    Pair<Graph<Node>, Integer> result = Pair.create(toGraph(resultGraph), minIndexFound);
    clearMinSCCState();
    return result;
  }

  private @NotNull Graph<Node> toGraph(Map<Node, Set<Node>> resultGraph) {
    return new Graph<Node>() {
      @Override
      public @NotNull Collection<Node> getNodes() {
        return resultGraph.keySet();
      }

      @Override
      public @NotNull Iterator<Node> getIn(Node node) {
        return Collections.emptyIterator();
      }

      @Override
      public @NotNull Iterator<Node> getOut(Node node) {
        Set<Node> nodes = resultGraph.get(node);
        return nodes != null ? nodes.iterator() : Collections.emptyIterator();
      }
    };
  }

  private boolean containsEdge(Graph<Node> graph, Node source, Node target) {
    Iterator<Node> successors = graph.getOut(source);
    while (successors.hasNext()) {
      Node successor = successors.next();
      if (successor.equals(target)) {
        return true;
      }
    }
    return false;
  }

  private List<Set<Node>> findSCCS(int startIndex) {
    // Find SCCs in the subgraph induced
    // by vertices startIndex and beyond.
    // A call to StrongConnectivityAlgorithm
    // would be too expensive because of the
    // need to materialize the subgraph.
    // So - do a local search by the Tarjan's
    // algorithm and pretend that vertices
    // with an index smaller than startIndex
    // do not exist.
    for (Node Node : myGraph.getNodes()) {
      int vI = toI(Node);
      if (vI < startIndex) {
        continue;
      }
      if (!myVIndex.containsKey(Node)) {
        getSCCs(startIndex, vI);
      }
    }
    List<Set<Node>> result = myFoundSCCs;
    myFoundSCCs = null;
    return result;
  }

  private void getSCCs(int startIndex, int vertexIndex) {
    Node vertex = toV(vertexIndex);
    myVIndex.put(vertex, myIndex);
    myVLowlink.put(vertex, myIndex);
    myIndex++;
    myPath.push(vertex);
    myPathSet.add(vertex);

    Set<Node> successors = new HashSet<>();
    myGraph.getOut(vertex).forEachRemaining(successors::add);
    for (Node successor : successors) {
      int successorIndex = toI(successor);
      if (successorIndex < startIndex) {
        continue;
      }
      if (!myVIndex.containsKey(successor)) {
        getSCCs(startIndex, successorIndex);
        myVLowlink.put(vertex, Math.min(myVLowlink.get(vertex), myVLowlink.get(successor)));
      }
      else if (myPathSet.contains(successor)) {
        myVLowlink.put(vertex, Math.min(myVLowlink.get(vertex), myVIndex.get(successor)));
      }
    }
    if (myVLowlink.get(vertex).equals(myVIndex.get(vertex))) {
      Set<Node> result = new HashSet<>();
      Node temp;
      do {
        temp = myPath.pop();
        myPathSet.remove(temp);
        result.add(temp);
      }
      while (!vertex.equals(temp));
      if (result.size() == 1) {
        Node Node = result.iterator().next();
        if (successors.contains(Node)) {
          myFoundSCCs.add(result);
        }
      }
      else {
        myFoundSCCs.add(result);
      }
    }
  }

  private boolean findCyclesInSCG(int startIndex, int vertexIndex, Graph<Node> scg) {
    /*
     * Find cycles in a strongly connected graph per Johnson.
     */
    boolean foundCycle = false;
    Node vertex = toV(vertexIndex);
    myStack.push(vertex);
    myBlocked.add(vertex);

    Set<Node> successors = new HashSet<>();
    scg.getOut(vertex).forEachRemaining(successors::add);
    for (Node successor : successors) {
      int successorIndex = toI(successor);
      if (successorIndex == startIndex) {
        List<Node> cycle = new ArrayList<>(myStack.size());
        myStack.descendingIterator().forEachRemaining(cycle::add);
        myCycleConsumer.accept(cycle);
        foundCycle = true;
      }
      else if (!myBlocked.contains(successor)) {
        boolean gotCycle = findCyclesInSCG(startIndex, successorIndex, scg);
        foundCycle = foundCycle || gotCycle;
      }
    }
    if (foundCycle) {
      unblock(vertex);
    }
    else {
      for (Node w : successors) {
        Set<Node> bSet = getBSet(w);
        bSet.add(vertex);
      }
    }
    myStack.pop();
    return foundCycle;
  }

  private void unblock(@NotNull Node vertex) {
    myBlocked.remove(vertex);
    Set<Node> bSet = getBSet(vertex);
    while (bSet.size() > 0) {
      Node w = bSet.iterator().next();
      bSet.remove(w);
      if (myBlocked.contains(w)) {
        unblock(w);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void initState(@NotNull Consumer<? super List<Node>> consumer) {
    myCycleConsumer = consumer;
    myIToV = (Node[])myGraph.getNodes().toArray();
    myVToI = new HashMap<>();
    myBlocked = new HashSet<>();
    myBSets = new HashMap<>();
    myStack = new ArrayDeque<>();

    for (int i = 0; i < myIToV.length; i++) {
      myVToI.put(myIToV[i], i);
    }
  }

  private void clearState() {
    myCycleConsumer = null;
    myIToV = null;
    myVToI = null;
    myBlocked = null;
    myBSets = null;
    myStack = null;
  }

  private void initMinSCGState() {
    myIndex = 0;
    myFoundSCCs = new ArrayList<>();
    myVIndex = new HashMap<>();
    myVLowlink = new HashMap<>();
    myPath = new ArrayDeque<>();
    myPathSet = new HashSet<>();
  }

  private void clearMinSCCState() {
    myIndex = 0;
    myFoundSCCs = null;
    myVIndex = null;
    myVLowlink = null;
    myPath = null;
    myPathSet = null;
  }

  private @NotNull Integer toI(@NotNull Node vertex) {
    return myVToI.get(vertex);
  }

  private @NotNull Node toV(@NotNull Integer i) {
    return myIToV[i];
  }

  private @NotNull Set<Node> getBSet(@NotNull Node Node) {
    // B sets typically not all needed,
    // so instantiate lazily.
    return myBSets.computeIfAbsent(Node, k -> new HashSet<>());
  }
}
