/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree;

import com.google.common.collect.Lists;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class LightTreeUtil {
  private LightTreeUtil() { }

  @Nullable
  public static LighterASTNode firstChildOfType(final LighterAST tree, final LighterASTNode node, final IElementType type) {
    for (final LighterASTNode child : tree.getChildren(node)) {
      if (child.getTokenType() == type) return child;
    }

    return null;
  }

  @NotNull
  public static LighterASTNode requiredChildOfType(final LighterAST tree, final LighterASTNode node, final IElementType type) {
    final LighterASTNode child = firstChildOfType(tree, node, type);
    assert child != null : "Required child " + type + " not found in " + node.getTokenType() + ": " + tree.getChildren(node);
    return child;
  }

  @NotNull
  public static List<LighterASTNode> getChildrenOfType(final LighterAST tree, final LighterASTNode node, final IElementType type) {
    final List<LighterASTNode> result = Lists.newArrayList();

    for (final LighterASTNode child : tree.getChildren(node)) {
      if (child.getTokenType() == type) result.add(child);
    }

    return result;
  }

  public static String toFilteredString(final LighterAST tree, final LighterASTNode node, @Nullable final TokenSet skipTypes) {
    final StringBuilder buffer = new StringBuilder(node.getEndOffset() - node.getStartOffset());
    toBuffer(tree, node, buffer, skipTypes);
    return buffer.toString();
  }

  private static void toBuffer(final LighterAST tree, final LighterASTNode node, final StringBuilder buffer, @Nullable final TokenSet skipTypes) {
    if (skipTypes != null && skipTypes.contains(node.getTokenType())) return;

    if (node instanceof LighterASTTokenNode) {
      buffer.append(((LighterASTTokenNode)node).getText());
      return;
    }

    if (node instanceof LighterLazyParseableNode) {
      buffer.append(((LighterLazyParseableNode)node).getText());
      return;
    }

    for (final LighterASTNode child : tree.getChildren(node)) {
      toBuffer(tree, child, buffer, skipTypes);
    }
  }
}