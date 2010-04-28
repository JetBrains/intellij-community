/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class EditSourceOnDoubleClickHandler {
  private EditSourceOnDoubleClickHandler() {
  }

  public static void install(final JTree tree, @Nullable final Runnable whenPerformed) {
    tree.addMouseListener(new TreeMouseListener(tree, whenPerformed));
  }

  public static void install(final JTree tree) {
    install(tree, null);
  }

  public static void install(final TreeTable treeTable) {
    treeTable.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (ModalityState.current().dominates(ModalityState.NON_MODAL)) return;
        if (treeTable.getTree().getPathForLocation(e.getX(), e.getY()) == null) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(treeTable);
        Project project = PlatformDataKeys.PROJECT.getData(dataContext);
        if (project == null) return;
        OpenSourceUtil.openSourcesFrom(dataContext, true);
      }
    });
  }

  public static void install(final Table table) {
    table.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (ModalityState.current().dominates(ModalityState.NON_MODAL)) return;
        if (table.columnAtPoint(e.getPoint()) < 0) return;
        if (table.rowAtPoint(e.getPoint()) < 0) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(table);
        Project project = PlatformDataKeys.PROJECT.getData(dataContext);
        if (project == null) return;
        OpenSourceUtil.openSourcesFrom(dataContext, true);
      }
    });
  }

  public static void install(final JList list,
                           final Runnable whenPerformed) {
    list.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        Point point = e.getPoint();
        int index = list.locationToIndex(point);
        if (index == -1) return;
        if (!list.getCellBounds(index, index).contains(point)) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(list);
        OpenSourceUtil.openSourcesFrom(dataContext, true);
        whenPerformed.run();
      }
    });
  }

  public static class TreeMouseListener extends MouseAdapter {
    private final JTree myTree;
    @Nullable private final Runnable myWhenPerformed;

    public TreeMouseListener(final JTree tree) {
      this(tree, null);
    }

    public TreeMouseListener(final JTree tree, @Nullable final Runnable whenPerformed) {
      myTree = tree;
      myWhenPerformed = whenPerformed;
    }

    public void mouseClicked(MouseEvent e) {
      if (MouseEvent.BUTTON1 != e.getButton() || e.getClickCount() != 2) return;

      if (myTree.getUI() instanceof UIUtil.MacTreeUI) {
        if (myTree.getClosestPathForLocation(e.getX(), e.getY()) == null) return;
      } else if (myTree.getPathForLocation(e.getX(), e.getY()) == null) return;

      DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (project == null) return;

      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath == null) return;
      final Object lastPathComponent = selectionPath.getLastPathComponent();
      if (((TreeNode)lastPathComponent).isLeaf() || !expandOnDoubleClick(((TreeNode)lastPathComponent))) {
        //Node expansion for non-leafs has a higher priority
        processDoubleClick(e, dataContext, (TreeNode) lastPathComponent);
      }
    }

    protected void processDoubleClick(final MouseEvent e, final DataContext dataContext, final TreeNode lastPathComponent) {
      OpenSourceUtil.openSourcesFrom(dataContext, true);
      if (myWhenPerformed != null) myWhenPerformed.run();
    }

    private static boolean expandOnDoubleClick(final TreeNode treeNode) {
      if (treeNode instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)treeNode).getUserObject();
        if (userObject instanceof NodeDescriptor) {
          return ((NodeDescriptor)userObject).expandOnDoubleClick();
        }
      }
      return true;
    }

  }
}
