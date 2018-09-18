// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author Eugene Zhuravlev
 */
public class ErrorViewTreeBuilder extends AbstractTreeBuilder{
  public ErrorViewTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure) {
    super(tree, treeModel, treeStructure, null);
  }

  @Override
  public void updateFromRoot() {
    if (isDisposed()) return;
    getUpdater().cancelAllRequests();
    super.updateFromRoot();
  }

  public void updateTree() {
    if (isDisposed()) return;
    getUpdater().addSubtreeToUpdate(getRootNode());
  }

  public void updateTree(Runnable runAferUpdate) {
    if (isDisposed()) return;
    getUpdater().runAfterUpdate(runAferUpdate);
    updateTree();
  }

  @Override
  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final ErrorTreeElement element = (ErrorTreeElement)nodeDescriptor.getElement();
    if (element instanceof GroupingElement) {
      return ((ErrorViewStructure)getTreeStructure()).getChildCount((GroupingElement)element) > 0;
    }
    return false;
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getParentDescriptor() == null || nodeDescriptor.getElement() instanceof GroupingElement;
  }

  @Override
  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}

