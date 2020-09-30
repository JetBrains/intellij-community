// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.Navigatable;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

public final class EditSourceOnDoubleClickHandler {
  private static final Key<Boolean> INSTALLED = Key.create("EditSourceOnDoubleClickHandlerInstalled");

  private EditSourceOnDoubleClickHandler() { }

  public static void install(final JTree tree, @Nullable final Runnable whenPerformed) {
    new TreeMouseListener(tree, whenPerformed).installOn(tree);
  }

  public static void install(final JTree tree) {
    install(tree, null);
  }

  public static void install(final TreeTable treeTable) {
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        if (ModalityState.current().dominates(ModalityState.NON_MODAL)) return false;
        if (treeTable.getTree().getPathForLocation(e.getX(), e.getY()) == null) return false;
        DataContext dataContext = DataManager.getInstance().getDataContext(treeTable);
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project == null) return false;
        OpenSourceUtil.openSourcesFrom(dataContext, true);
        return true;
      }
    }.installOn(treeTable);
  }

  public static void install(final JTable table) {
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        if (ModalityState.current().dominates(ModalityState.NON_MODAL)) return false;
        if (table.columnAtPoint(e.getPoint()) < 0) return false;
        if (table.rowAtPoint(e.getPoint()) < 0) return false;
        DataContext dataContext = DataManager.getInstance().getDataContext(table);
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project == null) return false;
        OpenSourceUtil.openSourcesFrom(dataContext, true);
        return true;
      }
    }.installOn(table);
  }

  public static void install(final JList list, final Runnable whenPerformed) {
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        Point point = e.getPoint();
        int index = list.locationToIndex(point);
        if (index == -1) return false;
        if (!list.getCellBounds(index, index).contains(point)) return false;
        DataContext dataContext = DataManager.getInstance().getDataContext(list);
        OpenSourceUtil.openSourcesFrom(dataContext, true);
        whenPerformed.run();
        return true;
      }
    }.installOn(list);
  }

  public static boolean isToggleEvent(@NotNull JTree tree, @NotNull MouseEvent e) {
    if (!SwingUtilities.isLeftMouseButton(e)) return false;
    int count = tree.getToggleClickCount();
    if (count <= 0 || e.getClickCount() % count != 0) return false;
    return isExpandPreferable(tree, tree.getSelectionPath());
  }

  /**
   * @return {@code true} to expand/collapse the node, {@code false} to navigate to source if possible
   */
  public static boolean isExpandPreferable(@NotNull JTree tree, @Nullable TreePath path) {
    if (path == null || Registry.is("ide.tree.expand.on.double.click.disabled", false)) return false;

    TreeModel model = tree.getModel();
    if (model == null || model.isLeaf(path.getLastPathComponent())) return false;
    if (!UIUtil.isClientPropertyTrue(tree, INSTALLED)) return true; // expand by default if handler is not installed

    // navigate to source is preferred if the tree provides a navigatable object for the given path
    if (!Registry.is("ide.tree.expand.navigatable.on.double.click.disabled", false)) {
      Navigatable navigatable = TreeUtil.getNavigatable(tree, path);
      if (navigatable != null && navigatable.canNavigateToSource()) return false;
    }
    // for backward compatibility
    NodeDescriptor<?> descriptor = TreeUtil.getLastUserObject(NodeDescriptor.class, path);
    return descriptor == null || descriptor.expandOnDoubleClick();
  }

  public static class TreeMouseListener extends DoubleClickListener {
    private final JTree myTree;
    @Nullable private final Runnable myWhenPerformed;

    public TreeMouseListener(final JTree tree) {
      this(tree, null);
    }

    public TreeMouseListener(final JTree tree, @Nullable final Runnable whenPerformed) {
      myTree = tree;
      myWhenPerformed = whenPerformed;
    }

    @Override
    public void installOn(@NotNull Component c, boolean allowDragWhileClicking) {
      super.installOn(c, allowDragWhileClicking);
      myTree.putClientProperty(INSTALLED, true);
    }

    @Override
    public void uninstall(Component c) {
      super.uninstall(c);
      myTree.putClientProperty(INSTALLED, null);
    }

    @Override
    public boolean onDoubleClick(@NotNull MouseEvent e) {
      TreePath clickPath = WideSelectionTreeUI.isWideSelection(myTree)
                           ? myTree.getClosestPathForLocation(e.getX(), e.getY())
                           : myTree.getPathForLocation(e.getX(), e.getY());
      if (clickPath == null) return false;

      final DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) return false;

      TreePath selectionPath = myTree.getSelectionPath();
      if (!clickPath.equals(selectionPath)) return false;

      //Node expansion for non-leafs has a higher priority
      if (isToggleEvent(myTree, e)) return false;

      processDoubleClick(e, dataContext, selectionPath);
      return true;
    }

    @SuppressWarnings("UnusedParameters")
    protected void processDoubleClick(@NotNull MouseEvent e, @NotNull DataContext dataContext, @NotNull TreePath treePath) {
      OpenSourceUtil.openSourcesFrom(dataContext, true);
      if (myWhenPerformed != null) myWhenPerformed.run();
    }
  }
}
