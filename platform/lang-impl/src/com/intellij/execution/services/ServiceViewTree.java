// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.execution.services.ServiceModel.ServiceViewItem;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;

import static com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED;

class ServiceViewTree extends Tree {
  private final TreeModel myTreeModel;

  ServiceViewTree(@NotNull TreeModel treeModel, @NotNull Disposable parent) {
    myTreeModel = treeModel;
    AsyncTreeModel asyncTreeModel = new AsyncTreeModel(myTreeModel, parent);
    setModel(asyncTreeModel);
    initTree();
  }

  private void initTree() {
    // look
    setRootVisible(false);
    setShowsRootHandles(true);
    setCellRenderer(new ServiceViewTreeCellRenderer());
    UIUtil.putClientProperty(this, ANIMATION_IN_RENDERER_ALLOWED, true);

    // listeners
    new TreeSpeedSearch(this, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true);
    ServiceViewTreeLinkMouseListener mouseListener = new ServiceViewTreeLinkMouseListener(this);
    mouseListener.installOn(this);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path == null) return false;

        Object lastComponent = path.getLastPathComponent();
        return myTreeModel.isLeaf(lastComponent) &&
               lastComponent instanceof ServiceViewItem &&
               ((ServiceViewItem)lastComponent).getViewDescriptor().handleDoubleClick(e);
      }
    }.installOn(this);

    // DnD
    //RowsDnDSupport.install(myTree, myTreeModel);
    setDragEnabled(true);
  }

  private static class ServiceViewTreeCellRenderer extends ServiceViewTreeCellRendererBase {
    private ServiceViewDescriptor myDescriptor;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      myDescriptor = value instanceof ServiceViewItem ? ((ServiceViewItem)value).getViewDescriptor() : null;
      super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
    }

    @Nullable
    @Override
    protected ItemPresentation getPresentation(Object node) {
      // Ensure that value != myTreeModel.getRoot() && !(value instanceof LoadingNode)
      if (!(node instanceof ServiceViewItem)) return null;

      ItemPresentation presentation = ((ServiceViewItem)node).getViewDescriptor().getPresentation();
      return presentation instanceof PresentationData ? presentation : new PresentationData(presentation.getPresentableText(),
                                                                                            presentation.getLocationString(),
                                                                                            presentation.getIcon(false),
                                                                                            null);
    }

    @Override
    protected Object getTag(String fragment) {
      return myDescriptor == null ? null : myDescriptor.getPresentationTag(fragment);
    }
  }
}
