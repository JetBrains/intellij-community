// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Comparator;

/**
 * @deprecated use {@link com.intellij.ui.tree.AsyncTreeModel} and {@link com.intellij.ui.tree.StructureTreeModel} instead.
 */
@Deprecated(forRemoval = true)
public class SimpleTreeBuilder extends AbstractTreeBuilder {
  public SimpleTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure, @Nullable Comparator comparator) {
    //noinspection unchecked
    super(tree, treeModel, treeStructure, comparator);
  }

  @Override
  public boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return ((SimpleNode) nodeDescriptor).isAlwaysShowPlus();
  }

  @Override
  public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return ((SimpleNode) nodeDescriptor).isAutoExpandNode();
  }

  @Override
  public final void updateFromRoot() {
    updateFromRoot(false);
  }

  public void updateFromRoot(boolean rebuild) {
    if (rebuild) {
      cleanUpStructureCaches();
    }

    if (EventQueue.isDispatchThread()) {
      super.queueUpdate();
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!isDisposed()) {
          super.queueUpdate();
        }
      });
    }
  }

  protected final DefaultMutableTreeNode createChildNode(final NodeDescriptor childDescr) {
    return new PatchedDefaultMutableTreeNode(childDescr);
  }

  private void cleanUpStructureCaches() {
    if (!(getTreeStructure() instanceof SimpleTreeStructure)) return;
    ((SimpleTreeStructure)getTreeStructure()).clearCaches();
  }

  public SimpleTreeBuilder initRoot() {
    initRootNode();
    return this;
  }

}
