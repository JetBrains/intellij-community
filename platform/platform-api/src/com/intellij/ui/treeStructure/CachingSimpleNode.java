// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CachingSimpleNode extends SimpleNode {

  private volatile SimpleNode[] myChildren;

  protected CachingSimpleNode(SimpleNode aParent) {
    super(aParent);
  }

  protected CachingSimpleNode(Project aProject, @Nullable NodeDescriptor aParentDescriptor) {
    super(aProject, aParentDescriptor);
  }

  @Override
  public final SimpleNode @NotNull [] getChildren() {
    SimpleNode[] cached = myChildren;
    if (cached != null) return cached;

    SimpleNode[] children = buildChildren();
    if (children == null) throw new NullPointerException("no children from " + getClass());
    for (int i = 0; i < children.length; i++) {
      if (children[i] == null) throw new NullPointerException("no child at " + i + " from " + getClass());
    }
    myChildren = children;
    onChildrenBuilt();
    return children;
  }

  protected void onChildrenBuilt() {
  }

  protected abstract SimpleNode[] buildChildren();

  public void cleanUpCache() {
    myChildren = null;
  }

  protected SimpleNode @Nullable [] getCached() {
    return myChildren;
  }

}
