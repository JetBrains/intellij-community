// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * This is a specialization of the Graph representing newly compiled/updated nodes and their corresponding structural information
 */
public interface Delta extends Graph {

  /**
   * @return true, if this Delta describes only changes in sources and no Nodes were generated, because compiler has not been run.
   *  Value false means the Delta object contains changes in sources and possibly in Nodes, because compiler has been run and new set of Nodes corresponding to the new content of sources has been generated
   */
  boolean isSourceOnly();

  /**
   * @param node    node reflecting compilation unit built by a compiler
   * @param sources sources, that were used to build the node
   */
  void associate(@NotNull Node<?, ?> node, @NotNull Iterable<NodeSource> sources);

  /**
   * @return The set of sources, which were the base for this delta.
   * Normally these are the sources sent to compiler for compilation, maybe augmented
   */
  Set<NodeSource> getBaseSources();

  Set<NodeSource> getDeletedSources();
}
