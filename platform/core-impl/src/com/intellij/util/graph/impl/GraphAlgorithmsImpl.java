// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.graph.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Chunk;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class GraphAlgorithmsImpl extends GraphAlgorithms {

  @Override
  public @NotNull <Node> Collection<Node> findNodesWhichBelongToAnyPathBetweenTwoNodes(
    @NotNull Graph<Node> graph,
    @NotNull Node start,
    @NotNull Node finish
  ) {
    final Set<Node> reachableFromStart = new HashSet<>();
    final Set<Node> leadToFinish = new HashSet<>();

    Dfs.performDfs(graph, start, reachableFromStart::add);
    Dfs.performDfs(invertEdgeDirections(graph), finish, leadToFinish::add);

    return ContainerUtil.intersection(reachableFromStart, leadToFinish);
  }

  @Override
  public @NotNull <Node> Collection<Node> findNodeNeighbourhood(
    @NotNull Graph<Node> graph,
    @NotNull Node node,
    int levelBound
  ) {
    final Set<Node> neighbourhood = new HashSet<>();
    Bfs.performBfs(graph, node, (neighbour, level) -> {
      if (level <= levelBound) {
        neighbourhood.add(neighbour);
      }
    });
    return neighbourhood;
  }

  @Override
  public @Nullable <Node> List<Node> findShortestPath(@NotNull InboundSemiGraph<Node> graph, @NotNull Node start, @NotNull Node finish) {
    return new ShortestPathFinder<>(graph).findPath(start, finish);
  }

  @Override
  public @NotNull <Node> List<List<Node>> findKShortestPaths(@NotNull Graph<Node> graph, @NotNull Node start, @NotNull Node finish, int k,
                                                             @NotNull ProgressIndicator progressIndicator) {
    return new KShortestPathsFinder<>(graph, start, finish, progressIndicator).findShortestPaths(k);
  }

  @Override
  public @NotNull <Node> Set<List<Node>> findCycles(@NotNull Graph<Node> graph, @NotNull Node node) {
    return new CycleFinder<>(graph).getNodeCycles(node);
  }

  @Override
  public <Node> void iterateOverAllSimpleCycles(@NotNull Graph<Node> graph, @NotNull Consumer<? super List<Node>> cycleConsumer) {
    new SimpleCyclesIterator<>(graph).iterateSimpleCycles(cycleConsumer);
  }

  @Override
  public @NotNull <Node> Graph<Node> invertEdgeDirections(final @NotNull Graph<Node> graph) {
    return new Graph<Node>() {
      @Override
      public @NotNull Collection<Node> getNodes() {
        return graph.getNodes();
      }

      @Override
      public @NotNull Iterator<Node> getIn(final Node n) {
        return graph.getOut(n);
      }

      @Override
      public @NotNull Iterator<Node> getOut(final Node n) {
        return graph.getIn(n);
      }
    };
  }

  @Override
  public @NotNull <Node> Graph<Chunk<Node>> computeSCCGraph(final @NotNull Graph<Node> graph) {
    final DFSTBuilder<Node> builder = new DFSTBuilder<>(graph);

    final Collection<Collection<Node>> components = builder.getComponents();
    final List<Chunk<Node>> chunks = new ArrayList<>(components.size());
    final Map<Node, Chunk<Node>> nodeToChunkMap = new LinkedHashMap<>();
    for (Collection<Node> component : components) {
      final Set<Node> chunkNodes = component.size() == 1
                                   ? Collections.singleton(component.iterator().next())
                                   : new LinkedHashSet<>(component);
      final Chunk<Node> chunk = new Chunk<>(chunkNodes);
      chunks.add(chunk);
      for (Node node : component) {
        nodeToChunkMap.put(node, chunk);
      }
    }

    return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<Chunk<Node>>() {
      @Override
      public @NotNull Collection<Chunk<Node>> getNodes() {
        return chunks;
      }

      @Override
      public @NotNull Iterator<Chunk<Node>> getIn(Chunk<Node> chunk) {
        final Set<Node> chunkNodes = chunk.getNodes();
        final Set<Chunk<Node>> ins = new LinkedHashSet<>();
        for (final Node node : chunkNodes) {
          for (Iterator<Node> nodeIns = graph.getIn(node); nodeIns.hasNext(); ) {
            final Node in = nodeIns.next();
            if (!chunk.containsNode(in)) {
              ins.add(nodeToChunkMap.get(in));
            }
          }
        }
        return ins.iterator();
      }
    }));
  }

  @Override
  public <Node> void collectOutsRecursively(@NotNull Graph<Node> graph, Node start, Set<? super Node> set) {
    if (!set.add(start)) {
      return;
    }

    List<Node> stack = new ArrayList<>();
    stack.add(start);
    while (!stack.isEmpty()) {
      Node currentNode = stack.remove(stack.size() - 1);
      Iterator<Node> successorIterator = graph.getOut(currentNode);
      while (successorIterator.hasNext()) {
        Node successor = successorIterator.next();
        if (set.add(successor)) {
          stack.add(successor);
        }
      }
    }
  }

  @Override
  public @NotNull <Node> Collection<Chunk<Node>> computeStronglyConnectedComponents(@NotNull Graph<Node> graph) {
    return computeSCCGraph(graph).getNodes();
  }

  @Override
  public @NotNull <Node> List<List<Node>> removePathsWithCycles(@NotNull List<? extends List<Node>> paths) {
    final List<List<Node>> result = new ArrayList<>();
    for (List<Node> path : paths) {
      if (!containsCycle(path)) {
        result.add(path);
      }
    }
    return result;
  }

  private static boolean containsCycle(List<?> path) {
    return new HashSet<Object>(path).size() != path.size();
  }
}