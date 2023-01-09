// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ASTStructure implements FlyweightCapableTreeStructure<ASTNode> {
  private final ASTNode myRoot;

  public ASTStructure(@NotNull ASTNode root) {
    myRoot = root;
  }

  @Override
  @NotNull
  public ASTNode getRoot() {
    return myRoot;
  }

  @Override
  public ASTNode getParent(@NotNull ASTNode node) {
    return node.getTreeParent();
  }

  @Override
  public int getChildren(@NotNull ASTNode astNode, @NotNull Ref<ASTNode[]> into) {
    ASTNode child = astNode.getFirstChildNode();
    if (child == null) return 0;

    ASTNode[] store = into.get();
    if (store == null) {
      store = new ASTNode[10];
      into.set(store);
    }

    int count = 0;
    while (child != null) {
      if (count >= store.length) {
        ASTNode[] newStore = Arrays.copyOf(store, count * 3 / 2);
        into.set(newStore);
        store = newStore;
      }
      store[count++] = child;
      child = child.getTreeNext();
    }

    return count;
  }

  @Override
  public void disposeChildren(ASTNode[] nodes, int count) {
  }

  @NotNull
  @Override
  public CharSequence toString(@NotNull ASTNode node) {
    return node.getChars();
  }

  @Override
  public int getStartOffset(@NotNull ASTNode node) {
    return node.getStartOffset();
  }

  @Override
  public int getEndOffset(@NotNull ASTNode node) {
    return node.getStartOffset() + node.getTextLength();
  }
}
