/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang;

import org.jetbrains.annotations.NotNull;

/**
 * Visits the nodes of a {@link LighterAST}.
 * <p>
 * The caller selects the method from the node kind.
 * It calls {@link #visitLazyParseableNode} for a {@link LighterLazyParseableNode},
 * {@link #visitTokenNode} for a {@link LighterASTTokenNode}, and {@link #visitNode} for all other nodes.
 * By default, the two specific methods delegate to {@link #visitNode}.
 * <p>
 * This interface does not walk the tree.
 * To visit the children of a node, use {@code RecursiveLighterASTNodeWalkingVisitor}.
 */
public interface LighterASTNodeVisitor {
  /**
   * Visits a node that has no more specific method in this interface.
   */
  void visitNode(@NotNull LighterASTNode node);

  /**
   * Visits a leaf node. By default, it calls {@link #visitNode}.
   */
  default void visitTokenNode(@NotNull LighterASTTokenNode node) {
    visitNode(node);
  }

  /**
   * Visits a node whose contents can stay unparsed. By default, it calls {@link #visitNode}.
   */
  default void visitLazyParseableNode(@NotNull LighterLazyParseableNode node) {
    visitNode(node);
  }
}
