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
package com.intellij.util.ui.tree;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

public final class TreeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.tree.TreeUtil");

  private TreeUtil() {}

  /**
   * @param tree JTree to collect expanded paths from.
   * @param paths output parameter.
   */
  public static void collectExpandedPaths(final JTree tree, final List<TreePath> paths){
    LOG.assertTrue(tree != null);
    LOG.assertTrue(paths != null);

    final TreeModel model = tree.getModel();
    final Object root = model.getRoot();
    LOG.assertTrue(root != null);

    collectExpandedPathsImpl(tree, paths, new TreePath(root));
  }

  public static List<TreePath> collectExpandedPaths(@NotNull final JTree tree){
    final ArrayList<TreePath> result = new ArrayList<TreePath>();
    final Object root = tree.getModel().getRoot();
    final TreePath rootPath = new TreePath(root);
    result.addAll(collectExpandedPaths(tree, rootPath));
    return result;
  }

  public static List<TreePath> collectExpandedPaths(final JTree tree, TreePath path){
    final ArrayList<TreePath> result = new ArrayList<TreePath>();
    if (!tree.isExpanded(path)) return result;
    final Object lastPathComponent = path.getLastPathComponent();
    final TreeModel model = tree.getModel();
    if (model.isLeaf(lastPathComponent)) {
      result.add(path);
    } else {
      boolean pathWasAdded = false;
      for(int i = model.getChildCount(lastPathComponent) - 1; i >= 0 ; i--){
        final TreePath childPath = path.pathByAddingChild(model.getChild(lastPathComponent, i));
        if (model.isLeaf(lastPathComponent)) {
          if (!pathWasAdded) {
            result.add(path);
            pathWasAdded= true;
          }
        }
        else if (tree.isExpanded(childPath)) {
          result.addAll(collectExpandedPaths(tree, childPath));
        } else {
          if (!pathWasAdded) {
            result.add(path);
            pathWasAdded= true;
          }
        }
      }

    }
    return result;
  }

  private static boolean collectExpandedPathsImpl(final JTree tree, final Collection<TreePath> paths, final TreePath path){
    final TreeModel model = tree.getModel();
    final Object lastPathComponent = path.getLastPathComponent();
    if(model.isLeaf(lastPathComponent)){
      return false;
    }

    boolean hasExpandedChildren = false;

    for(int i = model.getChildCount(lastPathComponent) - 1; i >= 0 ; i--){
      hasExpandedChildren |= collectExpandedPathsImpl(tree, paths, path.pathByAddingChild(model.getChild(lastPathComponent, i)));
    }

    if(!hasExpandedChildren){
      paths.add(path);
      return true;
    }
    else{
      return false;
    }
  }

  /**
   * Expands specified paths.
   * @param tree JTree to apply expansion status to
   * @param paths to expand. See {@link #collectExpandedPaths(JTree, List<TreePath>)}
   */
  public static void restoreExpandedPaths(final JTree tree, final List<TreePath> paths){
    LOG.assertTrue(tree != null);
    LOG.assertTrue(paths != null);

    for(int i = paths.size() - 1; i >= 0; i--){
      tree.expandPath(paths.get(i));
    }
  }

  public static TreePath getPath(final TreeNode aRootNode, final TreeNode aNode) {
    final List<TreeNode> pathStack = new ArrayList<TreeNode>();
    addEach(aRootNode, aNode, pathStack);

    final Object[] pathElements = new Object[pathStack.size()];

    for (int i = pathStack.size() - 1; i >= 0; i--) {
      pathElements[pathStack.size() - i - 1] = pathStack.get(i);
    }

    return new TreePath(pathElements);
  }

  public static boolean isAncestor(final TreeNode ancestor, final TreeNode node) {
    TreeNode parent = node;
    while (parent != null) {
      if (parent == ancestor) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private static boolean isAncestor(final TreePath ancestor, final TreePath path) {
    if (path.getPathCount() < ancestor.getPathCount()) return false;
    for (int i = 0; i < ancestor.getPathCount(); i++)
      if (!path.getPathComponent(i).equals(ancestor.getPathComponent(i))) return false;
    return true;
  }

  private static boolean isDescendants(final TreePath path, final TreePath[] paths) {
    for (final TreePath ancestor : paths) {
      if (isAncestor(ancestor, path)) return true;
    }
    return false;
  }

  public static TreePath getPathFromRoot(TreeNode node) {
    final ArrayList<TreeNode> path = new ArrayList<TreeNode>();
    do {
      path.add(node);
      node = node.getParent();
    } while (node != null);
    Collections.reverse(path);
    return new TreePath(path.toArray());
  }

  @Nullable
  public static TreeNode findNodeWithObject(final Object object, final TreeModel model, final Object parent) {
    for (int i = 0; i < model.getChildCount(parent); i++) {
      final DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) model.getChild(parent, i);
      if (childNode.getUserObject().equals(object)) return childNode;
    }
    return null;
  }

  /**
   * Removes last component in the current selection path.
   * @param tree to remove selected node from.
   */
  public static void removeSelected(final JTree tree) {
    LOG.assertTrue(tree != null);

    final TreePath selectionPath = tree.getSelectionPath();
    if (selectionPath == null) return;
    removeLastPathComponent((DefaultTreeModel) tree.getModel(), selectionPath).restoreSelection(tree);
  }

  public static void removeLastPathComponent(final JTree tree, final TreePath pathToBeRemoved){
    LOG.assertTrue(tree != null);
    LOG.assertTrue(pathToBeRemoved != null);

    removeLastPathComponent((DefaultTreeModel)tree.getModel(), pathToBeRemoved).restoreSelection(tree);
  }

  @Nullable
  public static DefaultMutableTreeNode findNodeWithObject(final DefaultMutableTreeNode aRoot, final Object aObject) {
    if (aRoot.getUserObject().equals(aObject)) {
      return aRoot;
    } else {
      for (int i = 0; i < aRoot.getChildCount(); i++) {
        final DefaultMutableTreeNode candidate = findNodeWithObject((DefaultMutableTreeNode) aRoot.getChildAt(i), aObject);
        if (null != candidate) {
          return candidate;
        }
      }
      return null;
    }
  }

  public static TreePath findCommonPath(final TreePath[] treePaths) {
    LOG.assertTrue(areComponentsEqual(treePaths, 0));
    TreePath result = new TreePath(treePaths[0].getPathComponent(0));
    int pathIndex = 1;
    while (areComponentsEqual(treePaths, pathIndex)) {
      result = result.pathByAddingChild(treePaths[0].getPathComponent(pathIndex));
      pathIndex++;
    }
    return result;
  }

  public static void selectFirstNode(final JTree tree) {
    final TreeModel model = tree.getModel();
    final Object root = model.getRoot();
    TreePath selectionPath = new TreePath(root);
    if (!tree.isRootVisible() && model.getChildCount(root) > 0)
      selectionPath = selectionPath.pathByAddingChild(model.getChild(root, 0));
    selectPath(tree, selectionPath);
  }

  private static void addEach(final TreeNode aRootNode, final TreeNode aNode, final List<TreeNode> aPathStack) {
    aPathStack.add(aNode);

    if (aNode != aRootNode) {
      addEach(aRootNode, aNode.getParent(), aPathStack);
    }
  }

  private static IndexTreePathState removeLastPathComponent(final DefaultTreeModel model, final TreePath pathToBeRemoved) {
    final IndexTreePathState selectionState = new IndexTreePathState(pathToBeRemoved);
    if (((MutableTreeNode) pathToBeRemoved.getLastPathComponent()).getParent() == null) return selectionState;
    model.removeNodeFromParent((MutableTreeNode) pathToBeRemoved.getLastPathComponent());
    return selectionState;
  }


  private static boolean areComponentsEqual(final TreePath[] paths, final int componentIndex) {
    if (paths[0].getPathCount() <= componentIndex) return false;
    final Object pathComponent = paths[0].getPathComponent(componentIndex);
    for (final TreePath treePath : paths) {
      if (treePath.getPathCount() <= componentIndex) return false;
      if (!pathComponent.equals(treePath.getPathComponent(componentIndex))) return false;
    }
    return true;
  }

  private static TreePath[] removeDuplicates(final TreePath[] paths) {
    final ArrayList<TreePath> result = new ArrayList<TreePath>();
    for (final TreePath path : paths) {
      if (!result.contains(path)) result.add(path);
    }
    return result.toArray(new TreePath[result.size()]);
  }

  public static TreePath[] selectMaximals(final TreePath[] paths) {
    if (paths == null) return new TreePath[0];
    final TreePath[] noDuplicates = removeDuplicates(paths);
    final ArrayList<TreePath> result = new ArrayList<TreePath>();
    for (final TreePath path : noDuplicates) {
      final ArrayList<TreePath> otherPaths = new ArrayList<TreePath>(Arrays.asList(noDuplicates));
      otherPaths.remove(path);
      if (!isDescendants(path, otherPaths.toArray(new TreePath[otherPaths.size()]))) result.add(path);
    }
    return result.toArray(new TreePath[result.size()]);
  }

  public static void sort(final DefaultTreeModel model, final Comparator comparator) {
    sort((DefaultMutableTreeNode) model.getRoot(), comparator);
  }

  public static void sort(final DefaultMutableTreeNode node, final Comparator comparator) {
    final List<TreeNode> children = childrenToArray(node);
    Collections.sort(children, comparator);
    node.removeAllChildren();
    addChildrenTo(node, children);
    for (int i = 0; i < node.getChildCount(); i++) {
      sort((DefaultMutableTreeNode) node.getChildAt(i), comparator);
    }
  }

  private static void addChildrenTo(final MutableTreeNode node, final List<TreeNode> children) {
    for (final Object aChildren : children) {
      final MutableTreeNode child = (MutableTreeNode)aChildren;
      node.insert(child, node.getChildCount());
    }
  }

  public static boolean traverse(final TreeNode node, final Traverse traverse) {
    final int childCount = node.getChildCount();
    for (int i = 0; i < childCount; i++){
      if (!traverse(node.getChildAt(i), traverse)) return false;
    }
    if (!traverse.accept(node)) return false;
    return true;
  }

  public static boolean traverseDepth(final TreeNode node, final Traverse traverse) {
    if (!traverse.accept(node)) return false;
    final int childCount = node.getChildCount();
    for (int i = 0; i < childCount; i++)
      if (!traverseDepth(node.getChildAt(i), traverse)) return false;
    return true;
  }

  public static void selectPath(final JTree tree, final TreePath path) {
    tree.makeVisible(path);
    showRowCentred(tree, tree.getRowForPath(path));
  }

  private static void moveDown(final JTree tree) {
    final int size = tree.getRowCount();
    int row = getSelectedRow(tree);
    if (row < size - 1) {
      row++;
      showAndSelect(tree, row, row + 2, row, true);
    }
  }

  private static void moveUp(final JTree tree) {
    int row = getSelectedRow(tree);
    if (row > 0) {
      row--;
      showAndSelect(tree, row - 2, row, row, true);
    }
  }

  private static void movePageUp(final JTree tree) {
    final int visible = getVisibleRowCount(tree);
    if (visible <= 0){
      moveHome(tree);
      return;
    }
    final int decrement = visible - 1;
    final int row = Math.max(getSelectedRow(tree) - decrement, 0);
    final int top = getFirstVisibleRow(tree) - decrement;
    final int bottom = top + visible - 1;
    showAndSelect(tree, top, bottom, row, true);
  }

  private static void movePageDown(final JTree tree) {
    final int visible = getVisibleRowCount(tree);
    if (visible <= 0){
      moveEnd(tree);
      return;
    }
    final int size = tree.getRowCount();
    final int increment = visible - 1;
    final int index = Math.min(getSelectedRow(tree) + increment, size - 1);
    final int top = getFirstVisibleRow(tree) + increment;
    final int bottom = top + visible - 1;
    showAndSelect(tree, top, bottom, index, true);
  }

  private static void moveHome(final JTree tree) {
    showRowCentred(tree, 0);
  }

  private static void moveEnd(final JTree tree) {
    showRowCentred(tree, tree.getRowCount() - 1);
  }

  private static void showRowCentred(final JTree tree, final int row) {
    showRowCentered(tree, row, true);
  }

  public static void showRowCentered(final JTree tree, final int row, final boolean centerHorizontally) {
    final int visible = getVisibleRowCount(tree);
    final int top = visible > 0 ? row - (visible - 1)/ 2 : row;
    final int bottom = visible > 0 ? top + visible - 1 : row;
    showAndSelect(tree, top, bottom, row, centerHorizontally);
  }

  private static void showAndSelect(final JTree tree, int top, int bottom, final int row, final boolean centerHorizontally) {
    final int size = tree.getRowCount();
    if (size == 0) {
      tree.clearSelection();
      return;
    }
    if (top < 0){
      top = 0;
    }
    if (bottom >= size){
      bottom = size - 1;
    }
    final Rectangle topBounds = tree.getRowBounds(top);
    final Rectangle bottomBounds = tree.getRowBounds(bottom);
    final Rectangle bounds;
    if (topBounds == null) {
      bounds = bottomBounds;
    }
    else if (bottomBounds == null) {
      bounds = topBounds;
    }
    else {
      bounds = topBounds.union(bottomBounds);
    }
    if (bounds != null) {
      final TreePath path = tree.getPathForRow(row);
      if (path != null && path.getParentPath() != null) {
        final Rectangle parentBounds = tree.getPathBounds(path.getParentPath());
        if (parentBounds != null) {
          bounds.x = parentBounds.x;
        }
      }
      if (!centerHorizontally) {
        bounds.x = 0;
        bounds.width = tree.getWidth();
      } else {
        bounds.width = Math.min(bounds.width, tree.getVisibleRect().width);
      }
      tree.scrollRectToVisible(bounds);
    }
    tree.setSelectionRow(row);
  }

  private static int getSelectedRow(final JTree tree) {
    return tree.getRowForPath(tree.getSelectionPath());
  }

  private static int getFirstVisibleRow(final JTree tree) {
    final Rectangle visible = tree.getVisibleRect();
    int row = -1;
    for (int i=0; i < tree.getRowCount(); i++) {
      final Rectangle bounds = tree.getRowBounds(i);
      if (visible.y <= bounds.y && visible.y + visible.height >= bounds.y + bounds.height) {
        row = i;
        break;
      }
    }
    return row;
  }

  private static int getVisibleRowCount(final JTree tree) {
    final Rectangle visible = tree.getVisibleRect();
    int count = 0;
    for (int i=0; i < tree.getRowCount(); i++) {
      final Rectangle bounds = tree.getRowBounds(i);
      if (visible.y <= bounds.y && visible.y + visible.height >= bounds.y + bounds.height) {
        count++;
      }
    }
    return count;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void installActions(final JTree tree) {
    tree.getActionMap().put("scrollUpChangeSelection", new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        movePageUp(tree);
      }
    });
    tree.getActionMap().put("scrollDownChangeSelection", new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        movePageDown(tree);
      }
    });
    tree.getActionMap().put("selectPrevious", new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        moveUp(tree);
      }
    });
    tree.getActionMap().put("selectNext", new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        moveDown(tree);
      }
    });
    copyAction(tree, "selectLast", "selectLastChangeLead");
    copyAction(tree, "selectFirst", "selectFirstChangeLead");
  }

  private static void copyAction(final JTree tree, String original, String copyTo) {
    final Action action = tree.getActionMap().get(original);
    if (action != null) {
      tree.getActionMap().put(copyTo, action);
    }
  }

  public static void collapseAll(final JTree tree, final int keepSelectionLevel) {
    final TreePath leadSelectionPath = tree.getLeadSelectionPath();
    // Collapse all
    int row = tree.getRowCount() - 1;
    while (row >= 0) {
      tree.collapseRow(row);
      row--;
    }
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();
    tree.expandPath(new TreePath(root));
    if (leadSelectionPath != null) {
      final Object[] path = leadSelectionPath.getPath();
      final Object[] pathToSelect = new Object[path.length > keepSelectionLevel && keepSelectionLevel >= 0 ? keepSelectionLevel : path.length];
      for (int i = 0; i < pathToSelect.length; i++) {
        pathToSelect[i] = path[i];
      }
      if (pathToSelect.length == 0) return;
      selectPath(tree, new TreePath(pathToSelect));
    }
  }

  public static void selectNode(final JTree tree, final TreeNode node) {
    selectPath(tree, getPathFromRoot(node));
  }

  public static void moveSelectedRow(final JTree tree, final int direction){
    final TreePath selectionPath = tree.getSelectionPath();
    final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)treeNode.getParent();
    final int idx = parent.getIndex(treeNode);
    parent.remove(treeNode);
    parent.insert(treeNode, idx + direction);
    ((DefaultTreeModel)tree.getModel()).reload(parent);
    TreeUtil.selectNode(tree, treeNode);
  }

  public static ArrayList<TreeNode> childrenToArray(final TreeNode node) {
    final ArrayList<TreeNode> result = new ArrayList<TreeNode>();
    for(int i = 0; i < node.getChildCount(); i++){
      result.add(node.getChildAt(i));
    }
    return result;
  }

  public static void expandRootChildIfOnlyOne(final JTree tree) {
    if (tree == null) return;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        if (root.getChildCount() == 1) {
          TreeNode firstChild = root.getFirstChild();
          tree.expandPath(new TreePath(new Object[]{root, firstChild}));
        }
      }
    });
  }

  public static void expandAll(final JTree tree) {
    int oldRowCount = 0;
    tree.expandPath(new TreePath(tree.getModel().getRoot()));
    do {
      int rowCount = tree.getRowCount();
      if (rowCount == oldRowCount) break;
      oldRowCount = rowCount;
      for (int i = 0; i < rowCount; i++) {
        tree.expandRow(i);
      }
    }
    while (true);
  }

  /**
   * Expands n levels of the tree counting from the root
   * @param tree to expand nodes of
   * @param levels depths of the expantion
   */
  public static void expand(JTree tree, int levels) {
    expand(tree, new TreePath(tree.getModel().getRoot()), levels);
  }

  private static void expand(JTree tree, TreePath path, int levels) {
    if (levels == 0) return;
    tree.expandPath(path);
    TreeNode node = (TreeNode)path.getLastPathComponent();
    Enumeration children = node.children();
    while (children.hasMoreElements()) {
      expand(tree, path.pathByAddingChild(children.nextElement()) , levels - 1);
    }
  }

  public static void selectInTree(DefaultMutableTreeNode node, boolean requestFocus, JTree tree) {
    if (node == null) return;

    final TreePath treePath = new TreePath(node.getPath());
    tree.expandPath(treePath);
    if (requestFocus) {
      tree.requestFocus();
    }
    selectPath(tree, treePath);
  }

  public static List<TreePath> collectSelectedPaths(final JTree tree, final TreePath treePath) {
    final ArrayList<TreePath> result = new ArrayList<TreePath>();
    final TreePath[] selections = tree.getSelectionPaths();
    if (selections != null) {
      for (TreePath selection : selections) {
        if (treePath.isDescendant(selection)) {
          result.add(selection);
        }
      }
    }
    return result;
  }

  public static void unselect(JTree tree, final DefaultMutableTreeNode node) {
    final TreePath rootPath = new TreePath(node.getPath());
    final TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      for (TreePath selectionPath : selectionPaths) {
        if (selectionPath.getPathCount() > rootPath.getPathCount() && rootPath.isDescendant(selectionPath)) {
          tree.removeSelectionPath(selectionPath);
        }
      }
    }
  }

  public interface RemoveNodeOperation {
    void removeNode(DefaultTreeModel model, TreePath path);
  }

  public interface Traverse{
    boolean accept(Object node);
  }

}
