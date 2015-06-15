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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.EditableTreeModel;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
class TreeToolbarDecorator extends ToolbarDecorator {
  private final JTree myTree;
  @Nullable private final ElementProducer<?> myProducer;

  TreeToolbarDecorator(JTree tree, @Nullable final ElementProducer<?> producer) {
    myTree = tree;
    myProducer = producer;
    myAddActionEnabled = myRemoveActionEnabled = myUpActionEnabled = myDownActionEnabled = myTree.getModel() instanceof EditableTreeModel;
    if (myTree.getModel() instanceof EditableTreeModel) {
      createDefaultTreeActions();
    }
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updateButtons();
      }
    });
    myTree.addPropertyChangeListener("enabled", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateButtons();
      }
    });
  }

  private void createDefaultTreeActions() {
    final EditableTreeModel model = (EditableTreeModel)myTree.getModel();
    myAddAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final TreePath path = myTree.getSelectionPath();
        final DefaultMutableTreeNode selected =
          path == null ? (DefaultMutableTreeNode)myTree.getModel().getRoot() : (DefaultMutableTreeNode)path.getLastPathComponent();
        final Object selectedNode = selected.getUserObject();

        myTree.stopEditing();
        Object element;
        if (model instanceof DefaultTreeModel && myProducer != null) {
           element = myProducer.createElement();
          if (element == null) return;
        } else {
          element = null;
        }
        DefaultMutableTreeNode parent = selected;
        if ((selectedNode instanceof SimpleNode && ((SimpleNode)selectedNode).isAlwaysLeaf()) || !selected.getAllowsChildren()) {
          parent = (DefaultMutableTreeNode)selected.getParent();
        }
        if (parent != null) {
         parent.insert(new DefaultMutableTreeNode(element), parent.getChildCount());
        }
        final TreePath createdPath = model.addNode(new TreePath(parent.getPath()));
        if (path != null) {
          TreeUtil.selectPath(myTree, createdPath);
          myTree.requestFocus();
        }
      }
    };

    myRemoveAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        myTree.stopEditing();
        if (myTree.getSelectionModel().getSelectionMode() == TreeSelectionModel.SINGLE_TREE_SELECTION) {
          final TreePath path = myTree.getSelectionPath();
          if (path != null) {
            model.removeNode(path);
          }
        }
        else {
          final TreePath[] paths = myTree.getSelectionPaths();
          if (paths != null && paths.length > 0) {
            model.removeNodes(Arrays.asList(paths));
          }
        }
      }
    };
  }

  @Override
  public ToolbarDecorator initPosition() {
    return setToolbarPosition(SystemInfo.isMac ? ActionToolbarPosition.BOTTOM : ActionToolbarPosition.TOP);
  }

  @Override
  protected JComponent getComponent() {
    return myTree;
  }

  @Override
  protected void updateButtons() {
    getActionsPanel().setEnabled(CommonActionsPanel.Buttons.REMOVE, myTree.getSelectionPath() != null);
  }

  @Override
  public ToolbarDecorator setVisibleRowCount(int rowCount) {
    myTree.setVisibleRowCount(rowCount);
    return this;
  }

  @Override
  protected boolean isModelEditable() {
    return myTree.getModel() instanceof EditableModel;
  }

  @Override
  protected void installDnDSupport() {
    RowsDnDSupport.install(myTree, (EditableModel)myTree.getModel());
  }
}
