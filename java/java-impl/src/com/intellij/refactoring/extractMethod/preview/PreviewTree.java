// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ui.tree.TreeUtil;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Pavel.Dolgov
 */
class PreviewTree implements Disposable {
  private final Project myProject;
  private final Tree myTree;
  private final List<PreviewTreeListener> myTreeListeners = new SmartList<>();
  private final PreviewTreeModel myModel;

  public PreviewTree(ExtractMethodProcessor processor) {
    myProject = processor.getProject();

    myModel = new PreviewTreeModel(processor);
    myTree = createTree(myModel);
    myTree.setPaintBusy(true);
  }

  private Tree createTree(DefaultTreeModel model) {
    Tree tree = new Tree(model);
    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setCellRenderer(new PreviewTreeRenderer());
    tree.setName("ExtractMethodPreview");
    tree.getSelectionModel().addTreeSelectionListener(
      e -> ApplicationManager.getApplication().invokeLater(
        () -> onSelectionUpdate()));

    SmartExpander.installOn(tree);
    TreeUtil.installActions(tree);
    TreeUtil.expand(tree, 2);
    TreeUtil.selectFirstNode(tree);

    PopupHandler.installPopupHandler(tree, IdeActions.EXTRACT_METHOD_TOOL_WINDOW_TREE_POPUP, ActionPlaces.UNKNOWN);
    return tree;
  }

  public void addTreeListener(PreviewTreeListener listener) {
    myTreeListeners.add(listener);
  }

  void onSelectionUpdate() {
    if (myProject.isDisposed()) return;

    FragmentNode firstSelectedNode = getFirstSelectedNode();
    if (firstSelectedNode != null) {
      for (PreviewTreeListener listener : myTreeListeners) {
        listener.onNodeSelected(firstSelectedNode);
      }
    }
  }

  @NotNull
  public List<FragmentNode> getSelectedNodes() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (ArrayUtil.isEmpty(selectionPaths)) {
      return Collections.emptyList();
    }
    List<FragmentNode> result = new ArrayList<>();
    for (TreePath path : selectionPaths) {
      result.addAll(getFragmentNodes(path));
    }
    return result;
  }

  @Nullable
  private FragmentNode getFirstSelectedNode() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (ArrayUtil.isEmpty(selectionPaths)) {
      return null;
    }
    for (TreePath path : selectionPaths) {
      List<FragmentNode> nodes = getFragmentNodes(path);
      if (!nodes.isEmpty()) {
        return nodes.get(0);
      }
    }
    return null;
  }

  @NotNull
  private static List<FragmentNode> getFragmentNodes(@NotNull TreePath path) {
    Object component = path.getLastPathComponent();
    if (component instanceof FragmentNode) {
      return Collections.singletonList((FragmentNode)component);
    }
    if (component instanceof TreeNode) {
      TreeNode node = (TreeNode)component;
      return IntStreamEx.range(0, node.getChildCount())
                        .mapToObj(node::getChildAt)
                        .select(FragmentNode.class)
                        .toList();
    }
    return Collections.emptyList();
  }

  public PreviewTreeModel getModel() {
    return myModel;
  }

  public JComponent getComponent() {
    return myTree;
  }

  public void repaint() {
    myTree.repaint();
  }

  void onUpdateLater() {
    myTree.setPaintBusy(false);
    onSelectionUpdate();
  }

  void selectNode(FragmentNode node) {
    myTree.setSelectionPath(new TreePath(node.getPath()));
  }

  public boolean isValid() {
    return myModel.isValid();
  }

  public void setValid(boolean valid) {
    myModel.setValid(valid);
    repaint();
  }

  @Override
  public void dispose() {
    myTreeListeners.clear();
  }
}
