// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.treeStructure.CachingTreePath;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.EditableTreeModel;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
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
  private final JComponent myComponent;
  private final JTree myTree;
  private final @Nullable ElementProducer<?> myProducer;

  TreeToolbarDecorator(JTree tree, final @Nullable ElementProducer<?> producer) {
    this(tree, tree, producer);
  }

  TreeToolbarDecorator(@NotNull JComponent component, @NotNull JTree tree, final @Nullable ElementProducer<?> producer) {
    myComponent = component;
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
        }
        else {
          element = null;
        }
        DefaultMutableTreeNode parent = selected;
        if ((selectedNode instanceof SimpleNode && ((SimpleNode)selectedNode).isAlwaysLeaf()) || !selected.getAllowsChildren()) {
          parent = (DefaultMutableTreeNode)selected.getParent();
        }
        if (parent != null) {
         parent.insert(new DefaultMutableTreeNode(element), parent.getChildCount());
        }
        final TreePath createdPath = model.addNode(new CachingTreePath(parent.getPath()));
        if (path != null) {
          TreeUtil.selectPath(myTree, createdPath);
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTree, true));
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
  protected @NotNull JComponent getComponent() {
    return myComponent;
  }

  @Override
  protected void updateButtons() {
    getActionsPanel().setEnabled(CommonActionsPanel.Buttons.REMOVE, myTree.getSelectionPath() != null);
  }

  @Override
  public @NotNull ToolbarDecorator setVisibleRowCount(int rowCount) {
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
