// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
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
    if (!(node instanceof CachingSimpleNode)) return;

    final CachingSimpleNode cachingNode = ((CachingSimpleNode) node);
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

    @NotNull
    @Override
    public Object getRootElement() {
      return myRoot;
    }
  }

}
