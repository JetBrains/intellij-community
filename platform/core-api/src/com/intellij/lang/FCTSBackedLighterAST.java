// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.util.Ref;
import com.intellij.util.CharTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.AbstractList;
import java.util.List;

public class FCTSBackedLighterAST extends LighterAST {
  private final @NotNull FlyweightCapableTreeStructure<LighterASTNode> myTreeStructure;

  public FCTSBackedLighterAST(@NotNull CharTable charTable, @NotNull FlyweightCapableTreeStructure<LighterASTNode> treeStructure) {
    super(charTable);
    myTreeStructure = treeStructure;
  }

  @Override
  public @NotNull LighterASTNode getRoot() {
    return myTreeStructure.getRoot();
  }

  @Override
  public LighterASTNode getParent(final @NotNull LighterASTNode node) {
    return myTreeStructure.getParent(node);
  }

  @Override
  public @Unmodifiable @NotNull List<LighterASTNode> getChildren(final @NotNull LighterASTNode parent) {
    final Ref<LighterASTNode[]> into = new Ref<>();
    final int numKids = myTreeStructure.getChildren(parent, into);
    if (numKids == 0) {
      return ContainerUtil.emptyList();
    }
    LighterASTNode[] elements = into.get();
    assert elements != null : myTreeStructure +" ("+parent+")";
    return new LighterASTNodeList(numKids, elements);
  }

  private static class LighterASTNodeList extends AbstractList<LighterASTNode> {
    private final int mySize;
    private final LighterASTNode[] myElements;

    LighterASTNodeList(int size, LighterASTNode[] elements) {
      mySize = size;
      myElements = elements;
    }

    @Override
    public LighterASTNode get(final int index) {
      if (index < 0 || index >= mySize) throw new IndexOutOfBoundsException("index:" + index + " size:" + mySize);
      return myElements[index];
    }

    @Override
    public int size() {
      return mySize;
    }
  }
}
