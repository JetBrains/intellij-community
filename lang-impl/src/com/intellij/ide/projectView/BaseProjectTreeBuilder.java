package com.intellij.ide.projectView;

import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class BaseProjectTreeBuilder extends AbstractTreeBuilder {
  protected final Project myProject;

  public BaseProjectTreeBuilder(Project project, JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure, Comparator<NodeDescriptor> comparator) {
    super(tree, treeModel, treeStructure, comparator);
    myProject = project;
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return ((AbstractTreeNode)nodeDescriptor).isAlwaysShowPlus();
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getParentDescriptor() == null;
  }

  protected final void expandNodeChildren(final DefaultMutableTreeNode node) {
    Object element = ((NodeDescriptor)node.getUserObject()).getElement();
    VirtualFile virtualFile = getFileToRefresh(element);
    super.expandNodeChildren(node);
    if (virtualFile != null) {
      virtualFile.refresh(true, false);
    }
  }

  private static VirtualFile getFileToRefresh(Object element) {
    return element instanceof PsiDirectory
           ? ((PsiDirectory)element).getVirtualFile()
           : element instanceof PsiFile ? ((PsiFile)element).getVirtualFile() : null;
  }

  private List<AbstractTreeNode> getOrBuildChildren(AbstractTreeNode parent) {
    buildNodeForElement(parent);

    DefaultMutableTreeNode node = getNodeForElement(parent);

    if (node == null) {
      return new ArrayList<AbstractTreeNode>();
    }

    getTree().expandPath(new TreePath(node.getPath()));

    int childCount = node.getChildCount();
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>(childCount);
    for (int i = 0; i < childCount; i++) {
      TreeNode childAt = node.getChildAt(i);
      DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode)childAt;
      if (defaultMutableTreeNode.getUserObject() instanceof AbstractTreeNode) {
        ProjectViewNode treeNode = (ProjectViewNode)defaultMutableTreeNode.getUserObject();
        result.add(treeNode);
      }
      else if (defaultMutableTreeNode.getUserObject() instanceof FavoritesTreeNodeDescriptor) {
        AbstractTreeNode treeNode = ((FavoritesTreeNodeDescriptor)defaultMutableTreeNode.getUserObject()).getElement();
        result.add(treeNode);
      }
    }
    return result;
  }

  private void hideChildrenFor(DefaultMutableTreeNode node) {
    if (node != null){
      final JTree tree = getTree();
      final TreePath path = new TreePath(node.getPath());
      if (tree.isExpanded(path)) {
        tree.collapsePath(path);
      }
    }
  }

  public ActionCallback select(Object element, VirtualFile file, boolean requestFocus) {
    DefaultMutableTreeNode selected = alreadySelectedNode(element);
    if (selected == null) {
      AbstractTreeNode node = expandPathTo(file, (AbstractTreeNode)getTreeStructure().getRootElement(), element, Conditions.<AbstractTreeNode>alwaysTrue());
      selected = getNodeForElement(node);
    }
    return TreeUtil.selectInTree(myProject, selected, requestFocus, getTree(), true);
  }

  public void selectInWidth(final Object element, final boolean requestFocus, final Condition<AbstractTreeNode> nonStopCondition) {
    DefaultMutableTreeNode selected = alreadySelectedNode(element);
    if (selected == null) {
      AbstractTreeNode node = expandPathTo(null, (AbstractTreeNode)getTreeStructure().getRootElement(), element, nonStopCondition);
      selected = getNodeForElement(node);
    }
    TreeUtil.selectInTree(selected, requestFocus, getTree());
  }

  // returns selected node for element or null if element node is not selected
  private DefaultMutableTreeNode alreadySelectedNode(final Object element) {
    final TreePath[] selectionPaths = getTree().getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) {
      return null;
    }
    for (TreePath selectionPath : selectionPaths) {
      Object selected = selectionPath.getLastPathComponent();
      if (elementIsEqualTo(selected, element)){
        return (DefaultMutableTreeNode)selected;
      }
    }
    return null;
  }

  private static boolean elementIsEqualTo(final Object node, final Object element) {
    if (node instanceof DefaultMutableTreeNode) {
      final Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (userObject instanceof ProjectViewNode) {
        final AbstractTreeNode projectViewNode = (ProjectViewNode)userObject;
        return projectViewNode.canRepresent(element);
      }
    }
    return false;
  }

  private AbstractTreeNode expandPathTo(VirtualFile file, AbstractTreeNode root, Object element, Condition<AbstractTreeNode> nonStopCondition) {
    if (root.canRepresent(element)) return root;
    if (root instanceof ProjectViewNode && file != null && !((ProjectViewNode)root).contains(file)) return null;

    DefaultMutableTreeNode currentNode = getNodeForElement(root);
    boolean expanded = currentNode != null && getTree().isExpanded(new TreePath(currentNode.getPath()));

    List<AbstractTreeNode> kids = getOrBuildChildren(root);
    for (AbstractTreeNode node : kids) {
      if (nonStopCondition.value(node)) {
        AbstractTreeNode result = expandPathTo(file, node, element, nonStopCondition);
        if (result != null) {
          currentNode = getNodeForElement(root);
          if (currentNode != null) {
            final TreePath path = new TreePath(currentNode.getPath());
            if (!getTree().isExpanded(path)) {
              getTree().expandPath(path);
            }
          }
          return result;
        }
        else if (!expanded) {
          hideChildrenFor(currentNode);
        }
      }
    }

    return null;
  }

  protected boolean validateNode(final Object child) {
    if (child instanceof ProjectViewNode) {
      final ProjectViewNode projectViewNode = (ProjectViewNode)child;
      return projectViewNode.validate();
    }
    return true;
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
