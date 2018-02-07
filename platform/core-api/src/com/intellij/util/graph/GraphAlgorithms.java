// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public abstract class GraphAlgorithms {
  public static GraphAlgorithms getInstance() {
    return ServiceManager.getService(GraphAlgorithms.class);
  }

  @Nullable
  public abstract <Node> List<Node> findShortestPath(@NotNull Graph<Node> graph, @NotNull Node start, @NotNull Node finish);

  @NotNull
  public abstract <Node> List<List<Node>> findKShortestPaths(@NotNull Graph<Node> graph, @NotNull Node start, @NotNull Node finish, int k,
                                                             @NotNull ProgressIndicator progressIndicator);

  @NotNull
  public abstract <Node> Set<List<Node>> findCycles(@NotNull Graph<Node> graph, @NotNull Node node);

  @NotNull
  public abstract <Node> List<List<Node>> removePathsWithCycles(@NotNull List<List<Node>> paths);

  @NotNull
  public abstract <Node> Graph<Node> invertEdgeDirections(@NotNull Graph<Node> graph);

  @NotNull
  public abstract <Node> Collection<Chunk<Node>> computeStronglyConnectedComponents(@NotNull Graph<Node> graph);

  @NotNull
  public abstract <Node> Graph<Chunk<Node>> computeSCCGraph(@NotNull Graph<Node> graph);

  /**
   * Adds start node and all its outs to given set recursively.
   * Nodes which are already in set aren't processed.
   *
   * @param start node to start from
   * @param set set to be populated
   */
  public abstract <Node> void collectOutsRecursively(@NotNull Graph<Node> graph, Node start, Set<Node> set);
}
