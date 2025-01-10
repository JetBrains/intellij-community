// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.ServiceViewItemState;
import com.intellij.execution.services.ServiceViewOptions;
import com.intellij.ide.DataManager;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.ColoredItem;
import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.function.Function;

import static com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED;

final class ServiceViewTree extends Tree {
  private static final Function<TreePath, String> DISPLAY_NAME_CONVERTER = path -> {
    Object node = path.getLastPathComponent();
    if (node instanceof ServiceViewItem) {
      return ServiceViewDragHelper.getDisplayName(((ServiceViewItem)node).getViewDescriptor().getPresentation());
    }
    return node.toString();
  };

  private final TreeModel myTreeModel;

  ServiceViewTree(@NotNull TreeModel treeModel, @NotNull Disposable parent) {
    myTreeModel = treeModel;
    AsyncTreeModel asyncTreeModel = new AsyncTreeModel(myTreeModel, parent);
    setModel(asyncTreeModel);
    initTree();
  }

  @Override
  public boolean isFileColorsEnabled() {
    return true;
  }

  @Override
  public @Nullable Color getFileColorFor(Object object) {
    if (object instanceof ColoredItem coloredItem) {
      return coloredItem.getColor();
    }
    return null;
  }

  private void initTree() {
    // look
    setRootVisible(false);
    setShowsRootHandles(true);
    setCellRenderer(new ServiceViewTreeCellRenderer());
    ComponentUtil.putClientProperty(this, ANIMATION_IN_RENDERER_ALLOWED, true);

    // listeners
    TreeSpeedSearch.installOn(this, true, DISPLAY_NAME_CONVERTER);
    ServiceViewTreeLinkMouseListener mouseListener = new ServiceViewTreeLinkMouseListener(this);
    mouseListener.installOn(this);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        TreePath path = getClosestPathForLocation(e.getX(), e.getY());
        if (path == null) return false;

        Object lastComponent = path.getLastPathComponent();
        if (lastComponent instanceof LoadingNode) return false;

        return myTreeModel.isLeaf(lastComponent) &&
               lastComponent instanceof ServiceViewItem &&
               ((ServiceViewItem)lastComponent).getViewDescriptor().handleDoubleClick(e);
      }
    }.installOn(this);
  }

  private static final class ServiceViewTreeCellRenderer extends ServiceViewTreeCellRendererBase {
    private ServiceViewDescriptor myDescriptor;
    private ServiceViewItemState myItemState;
    private JComponent myComponent;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      myDescriptor = value instanceof ServiceViewItem ? ((ServiceViewItem)value).getViewDescriptor() : null;
      myComponent = tree;
      myItemState = new ServiceViewItemState(selected, expanded, leaf, hasFocus);
      super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
      myDescriptor = null;
    }

    @Override
    protected @Nullable ItemPresentation getPresentation(Object node) {
      // Ensure that value != myTreeModel.getRoot() && !(value instanceof LoadingNode)
      if (!(node instanceof ServiceViewItem)) return null;

      ServiceViewOptions viewOptions = DataManager.getInstance().getDataContext(myComponent).getData(ServiceViewActionUtils.OPTIONS_KEY);
      assert myItemState != null;
      return ((ServiceViewItem)node).getItemPresentation(viewOptions, myItemState);
    }

    @Override
    protected Object getTag(String fragment) {
      return myDescriptor == null ? null : myDescriptor.getPresentationTag(fragment);
    }
  }
}
