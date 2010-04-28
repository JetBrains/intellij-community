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

package com.intellij.ide.projectView.impl;

import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.KeyEvent;

/**
 * @author Eugene Zhuravlev
 * Date: Sep 17, 2003
 * Time: 7:44:22 PM
 */
public abstract class ProjectViewTree extends DnDAwareTree {
  protected ProjectViewTree(TreeModel newModel) {
    super(newModel);

    final NodeRenderer renderer = new NodeRenderer();
    renderer.setOpaque(false);
    renderer.setIconOpaque(false);
    setCellRenderer(renderer);
  }

  @Override
  public void processKeyEvent(final KeyEvent e) {
    TreePath path = getSelectionPath();
    if (path != null) {
      if (e.getKeyCode() == KeyEvent.VK_LEFT) {
        if (isExpanded(path)) {
          collapsePath(path);
          return;
        }
      } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
        if (isCollapsed(path)) {
          expandPath(path);
          return;
        }
      }
    }

    super.processKeyEvent(e);
  }

  public final int getToggleClickCount() {
    DefaultMutableTreeNode node = getSelectedNode();
    if (node != null) {
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)userObject;
        if (!descriptor.expandOnDoubleClick()) return -1;
      }
    }
    return super.getToggleClickCount();
  }

  public abstract DefaultMutableTreeNode getSelectedNode();
}
