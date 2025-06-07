// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.graph;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public abstract class GraphAlgorithms {
  public static GraphAlgorithms getInstance() {
    return ApplicationManager.getApplication().getService(GraphAlgorithms.class);
  }

  public abstract @Unmodifiable <Node> @NotNull Collection<Node> findNodesWhichBelongToAnyPathBetweenTwoNodes(
    @NotNull Graph<Node> graph,
    @NotNull Node start,
    @NotNull Node finish
  );

  public abstract <Node> @NotNull Collection<Node> findNodeNeighbourhood(
    @NotNull Graph<Node> graph,
    @NotNull Node node,
    int levelBound
  );

  public abstract @Nullable @Unmodifiable <Node> List<Node> findShortestPath(@NotNull InboundSemiGraph<Node> graph, @NotNull Node start, @NotNull Node finish);

  public abstract @NotNull @Unmodifiable <Node> List<List<Node>> findKShortestPaths(@NotNull Graph<Node> graph, @NotNull Node start, @NotNull Node finish, int k,
                                                                      @NotNull ProgressIndicator progressIndicator);

  public abstract @NotNull @Unmodifiable <Node> Set<List<Node>> findCycles(@NotNull Graph<Node> graph, @NotNull Node node);

  public abstract <Node> void iterateOverAllSimpleCycles(
    @NotNull Graph<Node> graph,
    @NotNull Consumer<? super List<Node>> cycleConsumer
  );

  public abstract @NotNull <Node> List<List<Node>> removePathsWithCycles(@NotNull List<? extends List<Node>> paths);

  public abstract @NotNull <Node> Graph<Node> invertEdgeDirections(@NotNull Graph<Node> graph);

  public abstract @NotNull <Node> Collection<Chunk<Node>> computeStronglyConnectedComponents(@NotNull Graph<Node> graph);

  public abstract @NotNull <Node> Graph<Chunk<Node>> computeSCCGraph(@NotNull Graph<Node> graph);

  /**
   * Adds start node and all its outs to given set recursively.
   * Nodes which are already in set aren't processed.
   *
   * @param start node to start from
   * @param set   set to be populated
   */
  public abstract <Node> void collectOutsRecursively(@NotNull Graph<Node> graph, Node start, Set<? super Node> set);
}
