// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

@SuppressWarnings("ForLoopReplaceableByForEach")
public final class LightTreeUtil {

  public static @Nullable LighterASTNode firstChildOfType(@NotNull LighterAST tree, @Nullable LighterASTNode node, @NotNull IElementType type) {
    if (node == null) return null;

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0; i < children.size(); ++i) {
      LighterASTNode child = children.get(i);
      if (child.getTokenType() == type) return child;
    }
    return null;
  }

  public static @Nullable LighterASTNode firstChildOfType(@NotNull LighterAST tree, @Nullable LighterASTNode node, @NotNull TokenSet types) {
    if (node == null) return null;

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0; i < children.size(); ++i) {
      LighterASTNode child = children.get(i);
      if (types.contains(child.getTokenType())) return child;
    }

    return null;
  }

  public static @NotNull LighterASTNode requiredChildOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull IElementType type) {
    LighterASTNode child = firstChildOfType(tree, node, type);
    assert child != null : "Required child " + type + " not found in " + node.getTokenType() + ": " + tree.getChildren(node);
    return child;
  }

  public static @NotNull LighterASTNode requiredChildOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull TokenSet types) {
    LighterASTNode child = firstChildOfType(tree, node, types);
    assert child != null : "Required child " + types + " not found in " + node.getTokenType() + ": " + tree.getChildren(node);
    return child;
  }

  public static @NotNull List<LighterASTNode> getChildrenOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull IElementType type) {
    List<LighterASTNode> result = null;

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0, size = children.size(); i < size; ++i) {
      LighterASTNode child = children.get(i);
      if (child.getTokenType() == type) {
        if (result == null) result = new SmartList<>();
        result.add(child);
      }
    }

    return result != null ? result: Collections.emptyList();
  }

  public static @NotNull List<LighterASTNode> getChildrenOfType(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull TokenSet types) {
    List<LighterASTNode> children = tree.getChildren(node);
    List<LighterASTNode> result = null;

    for (int i = 0, size = children.size(); i < size; ++i) {
      LighterASTNode child = children.get(i);
      if (types.contains(child.getTokenType())) {
        if (result == null) result = new SmartList<>();
        result.add(child);
      }
    }

    return result != null ? result: Collections.emptyList();
  }

  public static @NotNull String toFilteredString(@NotNull LighterAST tree, @NotNull LighterASTNode node, @Nullable TokenSet skipTypes) {
    CharSequence text = getTextFromNode(node, skipTypes);
    if (text != null) {
      return text.toString();
    }

    int length = node.getEndOffset() - node.getStartOffset();
    if (length < 0) {
      length = 0;
      Logger.getInstance(LightTreeUtil.class).error("tree=" + tree + " node=" + node);
    }
    StringBuilder buffer = new StringBuilder(length);
    toBuffer(tree, node, buffer, skipTypes);
    return buffer.toString();
  }

  static void toBuffer(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StringBuilder buffer, @Nullable TokenSet skipTypes) {
    if (skipTypes != null && skipTypes.contains(node.getTokenType())) {
      return;
    }

    CharSequence text = getTextFromNode(node, skipTypes);
    if (text != null) {
      buffer.append(text);
      return;
    }

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0, size = children.size(); i < size; ++i) {
      toBuffer(tree, children.get(i), buffer, skipTypes);
    }
  }

  private static @Nullable CharSequence getTextFromNode(@NotNull LighterASTNode node, @Nullable TokenSet skipTypes) {
    if (node instanceof LighterASTTokenNode) {
      return ((LighterASTTokenNode)node).getText();
    }
    if (node instanceof LighterLazyParseableNode) {
      return ((LighterLazyParseableNode)node).getText();
    }
    // LighterASTSyntaxTreeBuilderBackedNode instances can be composite nodes. Therefore, when there
    // are tokens that should be filtered, we should not return the text directly.
    boolean filterTokens = skipTypes != null && skipTypes != TokenSet.EMPTY;
    if (!filterTokens && node instanceof LighterASTSyntaxTreeBuilderBackedNode) {
      return ((LighterASTSyntaxTreeBuilderBackedNode)node).getText();
    }
    return null;
  }

  public static @Nullable LighterASTNode getParentOfType(@NotNull LighterAST tree, @Nullable LighterASTNode node,
                                                         @NotNull TokenSet types, @NotNull TokenSet stopAt) {
    if (node == null) return null;
    node = tree.getParent(node);
    while (node != null) {
      IElementType type = node.getTokenType();
      if (types.contains(type)) return node;
      if (stopAt.contains(type)) return null;
      node = tree.getParent(node);
    }
    return null;
  }

  public static void processLeavesAtOffsets(int[] offsets, @NotNull LighterAST tree, @NotNull BiConsumer<? super LighterASTTokenNode, ? super Integer> consumer) {
    if (offsets.length == 0) return;

    int[] sortedOffsets = offsets.clone();
    Arrays.sort(sortedOffsets);
    new RecursiveLighterASTNodeWalkingVisitor(tree) {
      int nextIndex = 0;
      int nextOffset = sortedOffsets[0];

      @Override
      public void visitNode(@NotNull LighterASTNode element) {
        if (containsNextOffset(element)) {
          super.visitNode(element);
        }
      }

      @Override
      public void visitTokenNode(@NotNull LighterASTTokenNode node) {
        if (containsNextOffset(node)) {
          consumer.accept(node, nextOffset);
          while (containsNextOffset(node)) {
            advanceOffset();
          }
        }
      }

      private boolean containsNextOffset(@NotNull LighterASTNode element) {
        ProgressManager.checkCanceled();
        return nextIndex < sortedOffsets.length && element.getStartOffset() <= nextOffset && nextOffset < element.getEndOffset();
      }

      private void advanceOffset() {
        nextIndex++;
        if (nextIndex < sortedOffsets.length) {
          nextOffset = sortedOffsets[nextIndex];
        }
      }
    }.visitNode(tree.getRoot());
  }
}
