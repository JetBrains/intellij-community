/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

public class EditSourceOnDoubleClickHandler {
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
      protected boolean onDoubleClick(MouseEvent e) {
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
      protected boolean onDoubleClick(MouseEvent e) {
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
      protected boolean onDoubleClick(MouseEvent e) {
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
    public boolean onDoubleClick(MouseEvent e) {
      final TreePath clickPath = myTree.getUI() instanceof WideSelectionTreeUI ? myTree.getClosestPathForLocation(e.getX(), e.getY())
                                                                               : myTree.getPathForLocation(e.getX(), e.getY());
      if (clickPath == null) return false;

      final DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) return false;

      TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath == null || !clickPath.equals(selectionPath)) return false;
      Object last = selectionPath.getLastPathComponent();
      if (myTree.getModel().isLeaf(last) || myTree.getToggleClickCount() != e.getClickCount()) {
        //Node expansion for non-leafs has a higher priority
        processDoubleClick(e, dataContext, selectionPath);
        return true;
      }
      return false;
    }

    @SuppressWarnings("UnusedParameters")
    protected void processDoubleClick(@NotNull MouseEvent e, @NotNull DataContext dataContext, @NotNull TreePath treePath) {
      OpenSourceUtil.openSourcesFrom(dataContext, true);
      if (myWhenPerformed != null) myWhenPerformed.run();
    }

  }
}
