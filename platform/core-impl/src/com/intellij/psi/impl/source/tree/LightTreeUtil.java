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
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class LightTreeUtil {
  private LightTreeUtil() { }

  @Nullable
  public static LighterASTNode firstChildOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull IElementType type) {
    List<LighterASTNode> children = tree.getChildren(node);
    return firstChildOfType(children, type);
  }

  @Nullable
  public static LighterASTNode firstChildOfType(@NotNull List<LighterASTNode> children, @NotNull IElementType type) {
    for (int i = 0; i < children.size(); ++i) {
      LighterASTNode child = children.get(i);
      if (child.getTokenType() == type) return child;
    }

    return null;
  }

  @Nullable
  public static LighterASTNode firstChildOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull TokenSet types) {
    List<LighterASTNode> children = tree.getChildren(node);
    return firstChildOfType(children, types);
  }

  @Nullable
  public static LighterASTNode firstChildOfType(@NotNull List<LighterASTNode> children, @NotNull TokenSet types) {
    for (int i = 0; i < children.size(); ++i) {
      LighterASTNode child = children.get(i);
      if (types.contains(child.getTokenType())) return child;
    }

    return null;
  }

  @NotNull
  public static LighterASTNode requiredChildOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull IElementType type) {
    LighterASTNode child = firstChildOfType(tree, node, type);
    assert child != null : "Required child " + type + " not found in " + node.getTokenType() + ": " + tree.getChildren(node);
    return child;
  }

  @NotNull
  public static LighterASTNode requiredChildOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull TokenSet types) {
    LighterASTNode child = firstChildOfType(tree, node, types);
    assert child != null : "Required child " + types + " not found in " + node.getTokenType() + ": " + tree.getChildren(node);
    return child;
  }

  @NotNull
  public static List<LighterASTNode> getChildrenOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull IElementType type) {
    List<LighterASTNode> result = null;

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0, size = children.size(); i < size; ++i) {
      LighterASTNode child = children.get(i);
      if (child.getTokenType() == type) {
        if (result == null) result = new SmartList<LighterASTNode>();
        result.add(child);
      }
    }

    return result != null ? result: Collections.<LighterASTNode>emptyList();
  }

  @NotNull
  public static List<LighterASTNode> getChildrenOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull TokenSet types) {
    List<LighterASTNode> children = tree.getChildren(node);
    return getChildrenOfType(children, types);
  }

  @NotNull
  public static List<LighterASTNode> getChildrenOfType(List<LighterASTNode> children, @NotNull TokenSet types) {
    List<LighterASTNode> result = null;

    for (int i = 0, size = children.size(); i < size; ++i) {
      LighterASTNode child = children.get(i);
      if (types.contains(child.getTokenType())) {
        if (result == null) result = new SmartList<LighterASTNode>();
        result.add(child);
      }
    }

    return result != null ? result: Collections.<LighterASTNode>emptyList();
  }

  @NotNull
  public static String toFilteredString(@NotNull LighterAST tree, @NotNull LighterASTNode node, @Nullable TokenSet skipTypes) {
    int length = node.getEndOffset() - node.getStartOffset();
    if (length < 0) {
      length = 0;
      Logger.getInstance(LightTreeUtil.class).error("tree=" + tree + " node=" + node);
    }
    StringBuilder buffer = new StringBuilder(length);
    toBuffer(tree, node, buffer, skipTypes);
    return buffer.toString();
  }

  public static void toBuffer(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StringBuilder buffer, @Nullable TokenSet skipTypes) {
    if (skipTypes != null && skipTypes.contains(node.getTokenType())) {
      return;
    }

    if (node instanceof LighterASTTokenNode) {
      buffer.append(((LighterASTTokenNode)node).getText());
      return;
    }

    if (node instanceof LighterLazyParseableNode) {
      buffer.append(((LighterLazyParseableNode)node).getText());
      return;
    }

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0, size = children.size(); i < size; ++i) {
      toBuffer(tree, children.get(i), buffer, skipTypes);
    }
    tree.disposeChildren(children);
  }
}
