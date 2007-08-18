/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.treetable.TreeTable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class EditSourceOnDoubleClickHandler {
  public static void install(final JTree tree) {
    tree.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (tree.getPathForLocation(e.getX(), e.getY()) == null) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(tree);
        Project project = DataKeys.PROJECT.getData(dataContext);
        if (project == null) return;

        final TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) return;
        final Object lastPathComponent = selectionPath.getLastPathComponent();
        if (((TreeNode)lastPathComponent).isLeaf() || !expandOnDoubleClick(((TreeNode)lastPathComponent))) {
          //Node expansion for non-leafs has a higher priority
          OpenSourceUtil.openSourcesFrom(dataContext, true);
        }
      }

      private boolean expandOnDoubleClick(final TreeNode treeNode) {
        if (treeNode instanceof DefaultMutableTreeNode) {
          final Object userObject = ((DefaultMutableTreeNode)treeNode).getUserObject();
          if (userObject instanceof NodeDescriptor) {
            return ((NodeDescriptor)userObject).expandOnDoubleClick();
          }
        }
        return true;
      }

    });
  }
  public static void install(final TreeTable treeTable) {
    treeTable.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (treeTable.getTree().getPathForLocation(e.getX(), e.getY()) == null) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(treeTable);
        Project project = DataKeys.PROJECT.getData(dataContext);
        if (project == null) return;
        OpenSourceUtil.openSourcesFrom(dataContext, true);
      }
    });
  }

  public static void install(final Table table) {
    table.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (table.columnAtPoint(e.getPoint()) < 0) return;
        if (table.rowAtPoint(e.getPoint()) < 0) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(table);
        Project project = DataKeys.PROJECT.getData(dataContext);
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
}
