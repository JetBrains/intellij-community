// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleTreeStructure extends AbstractTreeStructure {

  @Override
  public Object @NotNull [] getChildElements(@NotNull Object element) {
    return ((SimpleNode) element).getChildren();
  }

  @Override
  public Object getParentElement(@NotNull Object element) {
    return ((SimpleNode) element).getParent();
  }

  @Override
  public boolean isAlwaysLeaf(@NotNull Object element) {
    return ((SimpleNode)element).isAlwaysLeaf();
  }

  @Override
  public @NotNull NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    return (NodeDescriptor) element;
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  public final void clearCaches() {
    cleanUpCaches((SimpleNode) getRootElement());
  }

  private static void cleanUpCaches(SimpleNode node) {
    if (!(node instanceof CachingSimpleNode cachingNode)) return;

    SimpleNode[] cached = cachingNode.getCached();
    if (cached == null) return;

    for (SimpleNode eachChild : cached) {
      cleanUpCaches(eachChild);
    }

    cachingNode.cleanUpCache();
  }

  public static class Impl extends SimpleTreeStructure {
    private final SimpleNode myRoot;

    public Impl(SimpleNode root) {
      myRoot = root;
    }

    @Override
    public @NotNull Object getRootElement() {
      return myRoot;
    }
  }

}
