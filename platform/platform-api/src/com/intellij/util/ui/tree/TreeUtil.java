/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Range;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public final class TreeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.tree.TreeUtil");
  @NonNls @NotNull private static final String TREE_UTIL_SCROLL_TIME_STAMP = "TreeUtil.scrollTimeStamp";

  private TreeUtil() {}

  /**
   * @param tree JTree to collect expanded paths from.
   * @param paths output parameter.
   */
  public static void collectExpandedPaths(@NotNull final JTree tree, @NotNull final List<TreePath> paths){
    final TreeModel model = tree.getModel();
    final Object root = model.getRoot();
    LOG.assertTrue(root != null);

    collectExpandedPathsImpl(tree, paths, new TreePath(root));
  }

  @NotNull
  public static List<TreePath> collectExpandedPaths(@NotNull final JTree tree){
    final ArrayList<TreePath> result = new ArrayList<>();
    final Object root = tree.getModel().getRoot();
    final TreePath rootPath = new TreePath(root);
    result.addAll(collectExpandedPaths(tree, rootPath));
    return result;
  }

  @NotNull
  public static <T> List<T> collectSelectedObjectsOfType(@NotNull JTree tree, @NotNull Class<T> clazz) {
    final TreePath[] selections = tree.getSelectionPaths();
    if (selections != null) {
      final ArrayList<T> result = new ArrayList<>();
      for (TreePath selection : selections) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selection.getLastPathComponent();
        final Object userObject = node.getUserObject();
        if (clazz.isInstance(userObject)) {
          //noinspection unchecked
          result.add((T)userObject);
        }
      }
      return result;
    }
    return Collections.emptyList();

  }

  @NotNull
  public static List<TreePath> collectExpandedPaths(@NotNull final JTree tree, @NotNull TreePath path){
    final ArrayList<TreePath> result = new ArrayList<>();
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

  private static boolean collectExpandedPathsImpl(@NotNull final JTree tree, @NotNull final Collection<TreePath> paths, @NotNull final TreePath path){
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
   * @param paths to expand. See {@link #collectExpandedPaths(javax.swing.JTree, java.util.List)}
   */
  public static void restoreExpandedPaths(@NotNull final JTree tree, @NotNull final List<TreePath> paths){
    for(int i = paths.size() - 1; i >= 0; i--){
      tree.expandPath(paths.get(i));
    }
  }

  @NotNull
  public static TreePath getPath(@NotNull TreeNode aRootNode, @NotNull TreeNode aNode) {
    TreeNode[] nodes = getPathFromRootTo(aRootNode, aNode, true);
    return new TreePath(nodes);
  }

  public static boolean isAncestor(@NotNull TreeNode ancestor, @NotNull TreeNode node) {
    TreeNode parent = node;
    while (parent != null) {
      if (parent == ancestor) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private static boolean isAncestor(@NotNull final TreePath ancestor, @NotNull final TreePath path) {
    if (path.getPathCount() < ancestor.getPathCount()) return false;
    for (int i = 0; i < ancestor.getPathCount(); i++)
      if (!path.getPathComponent(i).equals(ancestor.getPathComponent(i))) return false;
    return true;
  }

  private static boolean isDescendants(@NotNull final TreePath path, @NotNull final TreePath[] paths) {
    for (final TreePath ancestor : paths) {
      if (isAncestor(ancestor, path)) return true;
    }
    return false;
  }

  @NotNull
  public static TreePath getPathFromRoot(@NotNull TreeNode node) {
    TreeNode[] path = getPathFromRootTo(null, node, false);
    return new TreePath(path);
  }

  @NotNull
  private static TreeNode[] getPathFromRootTo(@Nullable TreeNode root, @NotNull TreeNode node, boolean includeRoot) {
    int height = 0;
    for (TreeNode n = node; n != root; n = n.getParent()) {
      height++;
    }
    TreeNode[] path = new TreeNode[includeRoot ? height+1 : height];
    int i = path.length-1;
    for (TreeNode n = node; i>=0; n = n.getParent()) {
      path[i--] = n;
    }
    return path;
  }

  @Nullable
  public static TreeNode findNodeWithObject(final Object object, @NotNull final TreeModel model, final Object parent) {
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
  public static void removeSelected(@NotNull final JTree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) {
      return;
    }
    for (TreePath path : paths) {
      removeLastPathComponent((DefaultTreeModel) tree.getModel(), path).restoreSelection(tree);
    }
  }

  public static void removeLastPathComponent(@NotNull final JTree tree, @NotNull final TreePath pathToBeRemoved){
    removeLastPathComponent((DefaultTreeModel)tree.getModel(), pathToBeRemoved).restoreSelection(tree);
  }

  @Nullable
  public static DefaultMutableTreeNode findNodeWithObject(@NotNull final DefaultMutableTreeNode aRoot, final Object aObject) {
    if (Comparing.equal(aRoot.getUserObject(), aObject)) {
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

  @NotNull
  public static TreePath findCommonPath(@NotNull final TreePath[] treePaths) {
    LOG.assertTrue(areComponentsEqual(treePaths, 0));
    TreePath result = new TreePath(treePaths[0].getPathComponent(0));
    int pathIndex = 1;
    while (areComponentsEqual(treePaths, pathIndex)) {
      result = result.pathByAddingChild(treePaths[0].getPathComponent(pathIndex));
      pathIndex++;
    }
    return result;
  }

  @NotNull
  public static ActionCallback selectFirstNode(@NotNull JTree tree) {
    TreePath selectionPath = getFirstNodePath(tree);
    return selectPath(tree, selectionPath);
  }

  @NotNull
  public static TreePath getFirstNodePath(@NotNull JTree tree) {
    final TreeModel model = tree.getModel();
    final Object root = model.getRoot();
    TreePath selectionPath = new TreePath(root);
    if (!tree.isRootVisible() && model.getChildCount(root) > 0) {
      selectionPath = selectionPath.pathByAddingChild(model.getChild(root, 0));
    }
    return selectionPath;
  }

  @NotNull
  public static TreePath getFirstLeafNodePath(@NotNull JTree tree) {
    final TreeModel model = tree.getModel();
    Object root = model.getRoot();
    TreePath selectionPath = new TreePath(root);
    while (model.getChildCount(root) > 0) {
      final Object child = model.getChild(root, 0);
      selectionPath = selectionPath.pathByAddingChild(child);
      root = child;
    }
    return selectionPath;
  }

  @NotNull
  private static IndexTreePathState removeLastPathComponent(@NotNull final DefaultTreeModel model, @NotNull final TreePath pathToBeRemoved) {
    final IndexTreePathState selectionState = new IndexTreePathState(pathToBeRemoved);
    if (((MutableTreeNode) pathToBeRemoved.getLastPathComponent()).getParent() == null) return selectionState;
    model.removeNodeFromParent((MutableTreeNode)pathToBeRemoved.getLastPathComponent());
    return selectionState;
  }


  private static boolean areComponentsEqual(@NotNull final TreePath[] paths, final int componentIndex) {
    if (paths[0].getPathCount() <= componentIndex) return false;
    final Object pathComponent = paths[0].getPathComponent(componentIndex);
    for (final TreePath treePath : paths) {
      if (treePath.getPathCount() <= componentIndex) return false;
      if (!pathComponent.equals(treePath.getPathComponent(componentIndex))) return false;
    }
    return true;
  }

  @NotNull
  private static TreePath[] removeDuplicates(@NotNull final TreePath[] paths) {
    final ArrayList<TreePath> result = new ArrayList<>();
    for (final TreePath path : paths) {
      if (!result.contains(path)) result.add(path);
    }
    return result.toArray(new TreePath[result.size()]);
  }

  @NotNull
  public static TreePath[] selectMaximals(@Nullable final TreePath[] paths) {
    if (paths == null) return new TreePath[0];
    final TreePath[] noDuplicates = removeDuplicates(paths);
    final ArrayList<TreePath> result = new ArrayList<>();
    for (final TreePath path : noDuplicates) {
      final ArrayList<TreePath> otherPaths = new ArrayList<>(Arrays.asList(noDuplicates));
      otherPaths.remove(path);
      if (!isDescendants(path, otherPaths.toArray(new TreePath[otherPaths.size()]))) result.add(path);
    }
    return result.toArray(new TreePath[result.size()]);
  }

  public static void sort(@NotNull final DefaultTreeModel model, @Nullable Comparator comparator) {
    sort((DefaultMutableTreeNode) model.getRoot(), comparator);
  }

  public static void sort(@NotNull final DefaultMutableTreeNode node, @Nullable Comparator comparator) {
    final List<TreeNode> children = childrenToArray(node);
    Collections.sort(children, comparator);
    node.removeAllChildren();
    addChildrenTo(node, children);
    for (int i = 0; i < node.getChildCount(); i++) {
      sort((DefaultMutableTreeNode) node.getChildAt(i), comparator);
    }
  }

  public static void addChildrenTo(@NotNull final MutableTreeNode node, @NotNull final List<TreeNode> children) {
    for (final Object aChildren : children) {
      final MutableTreeNode child = (MutableTreeNode)aChildren;
      node.insert(child, node.getChildCount());
    }
  }

  public static boolean traverse(@NotNull final TreeNode node, @NotNull final Traverse traverse) {
    final int childCount = node.getChildCount();
    for (int i = 0; i < childCount; i++){
      if (!traverse(node.getChildAt(i), traverse)) return false;
    }
    return traverse.accept(node);
  }

  public static boolean traverseDepth(@NotNull final TreeNode node, @NotNull final Traverse traverse) {
    if (!traverse.accept(node)) return false;
    final int childCount = node.getChildCount();
    for (int i = 0; i < childCount; i++)
      if (!traverseDepth(node.getChildAt(i), traverse)) return false;
    return true;
  }

  @NotNull
  public static ActionCallback selectPath(@NotNull final JTree tree, final TreePath path) {
    return selectPath(tree, path, true);
  }

  @NotNull
  public static ActionCallback selectPath(@NotNull final JTree tree, final TreePath path, boolean center) {
    tree.makeVisible(path);
    if (center) {
      return showRowCentred(tree, tree.getRowForPath(path));
    } else {
      final int row = tree.getRowForPath(path);
      return showAndSelect(tree, row - ScrollingUtil.ROW_PADDING, row + ScrollingUtil.ROW_PADDING, row, -1);
    }
  }

  @NotNull
  public static ActionCallback moveDown(@NotNull final JTree tree) {
    final int size = tree.getRowCount();
    int row = tree.getLeadSelectionRow();
    if (row < size - 1) {
      row++;
      return showAndSelect(tree, row, row + 2, row, getSelectedRow(tree), false, true, true);
    } else {
      return ActionCallback.DONE;
    }
  }

  @NotNull
  public static ActionCallback moveUp(@NotNull final JTree tree) {
    int row = tree.getLeadSelectionRow();
    if (row > 0) {
      row--;
      return showAndSelect(tree, row - 2, row, row, getSelectedRow(tree), false, true, true);
    } else {
      return ActionCallback.DONE;
    }
  }

  @NotNull
  public static ActionCallback movePageUp(@NotNull final JTree tree) {
    final int visible = getVisibleRowCount(tree);
    if (visible <= 0){
      return moveHome(tree);
    }
    final int decrement = visible - 1;
    final int row = Math.max(getSelectedRow(tree) - decrement, 0);
    final int top = getFirstVisibleRow(tree) - decrement;
    final int bottom = top + visible - 1;
    return showAndSelect(tree, top, bottom, row, getSelectedRow(tree));
  }

  @NotNull
  public static ActionCallback movePageDown(@NotNull final JTree tree) {
    final int visible = getVisibleRowCount(tree);
    if (visible <= 0){
      return moveEnd(tree);
    }
    final int size = tree.getRowCount();
    final int increment = visible - 1;
    final int index = Math.min(getSelectedRow(tree) + increment, size - 1);
    final int top = getFirstVisibleRow(tree) + increment;
    final int bottom = top + visible - 1;
    return showAndSelect(tree, top, bottom, index, getSelectedRow(tree));
  }

  @NotNull
  private static ActionCallback moveHome(@NotNull final JTree tree) {
    return showRowCentred(tree, 0);
  }

  @NotNull
  private static ActionCallback moveEnd(@NotNull final JTree tree) {
    return showRowCentred(tree, tree.getRowCount() - 1);
  }

  @NotNull
  private static ActionCallback showRowCentred(@NotNull final JTree tree, final int row) {
    return showRowCentered(tree, row, true);
  }

  @NotNull
  public static ActionCallback showRowCentered(@NotNull final JTree tree, final int row, final boolean centerHorizontally) {
    return showRowCentered(tree, row, centerHorizontally, true);
  }

  @NotNull
  public static ActionCallback showRowCentered(@NotNull final JTree tree, final int row, final boolean centerHorizontally, boolean scroll) {
    final int visible = getVisibleRowCount(tree);

    final int top = visible > 0 ? row - (visible - 1)/ 2 : row;
    final int bottom = visible > 0 ? top + visible - 1 : row;
    return showAndSelect(tree, top, bottom, row, -1, false, scroll, false);
  }

  @NotNull
  public static ActionCallback showAndSelect(@NotNull final JTree tree, int top, int bottom, final int row, final int previous) {
    return showAndSelect(tree, top, bottom, row, previous, false);
  }

  @NotNull
  public static ActionCallback showAndSelect(@NotNull final JTree tree, int top, int bottom, final int row, final int previous, boolean addToSelection) {
    return showAndSelect(tree, top, bottom, row, previous, addToSelection, true, false);
  }

  @NotNull
  public static ActionCallback showAndSelect(@NotNull final JTree tree, int top, int bottom, final int row, final int previous, final boolean addToSelection, final boolean scroll) {
    return showAndSelect(tree, top, bottom, row, previous, addToSelection, scroll, false);
  }

  @NotNull
  public static ActionCallback showAndSelect(@NotNull final JTree tree, int top, int bottom, final int row, final int previous, final boolean addToSelection, final boolean scroll, final boolean resetSelection) {
    final TreePath path = tree.getPathForRow(row);

    if (path == null) return ActionCallback.DONE;

    final int size = tree.getRowCount();
    if (size == 0) {
      tree.clearSelection();
      return ActionCallback.DONE;
    }
    if (top < 0){
      top = 0;
    }
    if (bottom >= size){
      bottom = size - 1;
    }

    if (row >= tree.getRowCount()) return ActionCallback.DONE;

    boolean okToScroll = true;
    if (tree.isShowing()) {
      if (!tree.isValid()) {
        tree.validate();
      }
    } else {
      Application app = ApplicationManager.getApplication();
      if (app != null && app.isUnitTestMode()) {
        okToScroll = false;
      }
    }

    Runnable selectRunnable = () -> {
      if (!tree.isRowSelected(row)) {
        if (addToSelection) {
          tree.getSelectionModel().addSelectionPath(tree.getPathForRow(row));
        } else {
          tree.setSelectionRow(row);
        }
      } else if (resetSelection) {
        if (!addToSelection) {
          tree.setSelectionRow(row);
        }
      }
    };


    if (!okToScroll) {
      selectRunnable.run();
      return ActionCallback.DONE;
    }


    final Rectangle rowBounds = tree.getRowBounds(row);
    if (rowBounds == null) return ActionCallback.DONE;

    Rectangle topBounds = tree.getRowBounds(top);
    if (topBounds == null) {
      topBounds = rowBounds;
    }

    Rectangle bottomBounds = tree.getRowBounds(bottom);
    if (bottomBounds == null) {
      bottomBounds = rowBounds;
    }

    Rectangle bounds = topBounds.union(bottomBounds);
    bounds.x = rowBounds.x;
    bounds.width = rowBounds.width;

    final Rectangle visible = tree.getVisibleRect();
    if (visible.contains(bounds)) {
      bounds = null;
    } else {
      final Component comp =
        tree.getCellRenderer().getTreeCellRendererComponent(tree, path.getLastPathComponent(), true, true, false, row, false);

      if (comp instanceof SimpleColoredComponent) {
        final SimpleColoredComponent renderer = (SimpleColoredComponent)comp;
        final Dimension scrollableSize = renderer.computePreferredSize(true);
        bounds.width = scrollableSize.width;
      }
    }

    final ActionCallback callback = new ActionCallback();


    selectRunnable.run();

    if (bounds != null) {
      final Range<Integer> range = getExpandControlRange(tree, path);
      if (range != null) {
        int delta = bounds.x - range.getFrom().intValue();
        bounds.x -= delta;
        bounds.width -= delta;
      }

      if (visible.width < bounds.width) {
        bounds.width = visible.width;
      }

      if (tree instanceof Tree && !((Tree)tree).isHorizontalAutoScrollingEnabled()) {
        bounds.x = 0;
      }

      final Rectangle b1 = bounds;
      final Runnable runnable = () -> {
        if (scroll) {
          AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(tree);
          if (builder != null) {
            builder.getReady(TreeUtil.class).doWhenDone(() -> tree.scrollRectToVisible(b1));
            callback.setDone();
          } else {
            tree.scrollRectToVisible(b1);

            Long ts = (Long)tree.getClientProperty(TREE_UTIL_SCROLL_TIME_STAMP);
            if (ts == null) {
              ts = 0L;
            }
            ts = ts.longValue() + 1;
            tree.putClientProperty(TREE_UTIL_SCROLL_TIME_STAMP, ts);

            final long targetValue = ts.longValue();

            SwingUtilities.invokeLater(() -> {
              Long actual = (Long)tree.getClientProperty(TREE_UTIL_SCROLL_TIME_STAMP);
              if (actual == null || targetValue < actual.longValue()) return;

              if (!tree.getVisibleRect().contains(b1)) {
                tree.scrollRectToVisible(b1);
              }
              callback.setDone();
            });
          }
        }
        callback.setDone();
      };

      runnable.run();

    } else {
      callback.setDone();
    }

    return callback;
  }


  // this method returns FIRST selected row but not LEAD
  private static int getSelectedRow(@NotNull final JTree tree) {
    return tree.getRowForPath(tree.getSelectionPath());
  }

  private static int getFirstVisibleRow(@NotNull final JTree tree) {
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

  public static int getVisibleRowCount(@NotNull final JTree tree) {
    final Rectangle visible = tree.getVisibleRect();

    if (visible == null) return 0;

    int count = 0;
    for (int i=0; i < tree.getRowCount(); i++) {
      final Rectangle bounds = tree.getRowBounds(i);
      if (bounds == null) continue;
      if (visible.y <= bounds.y && visible.y + visible.height >= bounds.y + bounds.height) {
        count++;
      }
    }
    return count;
  }

  /**
   * works correctly for trees with fixed row height only.
   * For variable height trees (e.g. trees with custom tree node renderer) use the {@link #getVisibleRowCount(JTree)} which is slower
   */
  public static int getVisibleRowCountForFixedRowHeight(@NotNull final JTree tree) {
    // myTree.getVisibleRowCount returns 20
    Rectangle bounds = tree.getRowBounds(0);
    int rowHeight = bounds == null ? 0 : bounds.height;
    return rowHeight == 0 ? tree.getVisibleRowCount() : tree.getVisibleRect().height / rowHeight;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void installActions(@NotNull final JTree tree) {
    tree.getActionMap().put("scrollUpChangeSelection", new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        movePageUp(tree);
      }
    });
    tree.getActionMap().put("scrollDownChangeSelection", new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        movePageDown(tree);
      }
    });
    tree.getActionMap().put("selectPrevious", new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        moveUp(tree);
      }
    });
    tree.getActionMap().put("selectNext", new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        moveDown(tree);
      }
    });
    copyAction(tree, "selectLast", "selectLastChangeLead");
    copyAction(tree, "selectFirst", "selectFirstChangeLead");

    InputMap inputMap = tree.getInputMap(JComponent.WHEN_FOCUSED);
    UIUtil.maybeInstall(inputMap, "scrollUpChangeSelection", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0));
    UIUtil.maybeInstall(inputMap, "scrollDownChangeSelection", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0));
    UIUtil.maybeInstall(inputMap, "selectNext", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
    UIUtil.maybeInstall(inputMap, "selectPrevious", KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
    UIUtil.maybeInstall(inputMap, "selectLast", KeyStroke.getKeyStroke(KeyEvent.VK_END, 0));
    UIUtil.maybeInstall(inputMap, "selectFirst", KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0));
  }

  private static void copyAction(@NotNull final JTree tree, String original, String copyTo) {
    final Action action = tree.getActionMap().get(original);
    if (action != null) {
      tree.getActionMap().put(copyTo, action);
    }
  }

  public static void collapseAll(@NotNull final JTree tree, final int keepSelectionLevel) {
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
      System.arraycopy(path, 0, pathToSelect, 0, pathToSelect.length);
      if (pathToSelect.length == 0) return;
      selectPath(tree, new TreePath(pathToSelect));
    }
  }

  public static void selectNode(@NotNull final JTree tree, final TreeNode node) {
    selectPath(tree, getPathFromRoot(node));
  }

  public static void moveSelectedRow(@NotNull final JTree tree, final int direction){
    final TreePath selectionPath = tree.getSelectionPath();
    final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)treeNode.getParent();
    final int idx = parent.getIndex(treeNode);
    ((DefaultTreeModel)tree.getModel()).removeNodeFromParent(treeNode);
    ((DefaultTreeModel)tree.getModel()).insertNodeInto(treeNode, parent, idx + direction);
    selectNode(tree, treeNode);
  }

  @NotNull
  public static ArrayList<TreeNode> childrenToArray(@NotNull final TreeNode node) {
    //ApplicationManager.getApplication().assertIsDispatchThread();
    final int size = node.getChildCount();
    final ArrayList<TreeNode> result = new ArrayList<>(size);
    for(int i = 0; i < size; i++){
      TreeNode child = node.getChildAt(i);
      LOG.assertTrue(child != null);
      result.add(child);
    }
    return result;
  }

  public static void expandRootChildIfOnlyOne(@Nullable final JTree tree) {
    if (tree == null) return;
    final Runnable runnable = () -> {
      final DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();
      tree.expandPath(new TreePath(new Object[]{root}));
      if (root.getChildCount() == 1) {
        TreeNode firstChild = root.getFirstChild();
        tree.expandPath(new TreePath(new Object[]{root, firstChild}));
      }
    };
    UIUtil.invokeLaterIfNeeded(runnable);
  }

  public static void expandAll(@NotNull final JTree tree) {
    tree.expandPath(new TreePath(tree.getModel().getRoot()));
    int oldRowCount = 0;
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
  public static void expand(@NotNull JTree tree, int levels) {
    expand(tree, new TreePath(tree.getModel().getRoot()), levels);
  }

  private static void expand(@NotNull JTree tree, @NotNull TreePath path, int levels) {
    if (levels == 0) return;
    tree.expandPath(path);
    TreeNode node = (TreeNode)path.getLastPathComponent();
    Enumeration children = node.children();
    while (children.hasMoreElements()) {
      expand(tree, path.pathByAddingChild(children.nextElement()) , levels - 1);
    }
  }

  @NotNull
  public static ActionCallback selectInTree(DefaultMutableTreeNode node, boolean requestFocus, @NotNull JTree tree) {
    return selectInTree(node, requestFocus, tree, true);
  }

  @NotNull
  public static ActionCallback selectInTree(@Nullable DefaultMutableTreeNode node, boolean requestFocus, @NotNull JTree tree, boolean center) {
    if (node == null) return ActionCallback.DONE;

    final TreePath treePath = new TreePath(node.getPath());
    tree.expandPath(treePath);
    if (requestFocus) {
      tree.requestFocus();
    }
    return selectPath(tree, treePath, center);
  }

  @NotNull
  public static ActionCallback selectInTree(Project project, @Nullable DefaultMutableTreeNode node, boolean requestFocus, @NotNull JTree tree, boolean center) {
    if (node == null) return ActionCallback.DONE;

    final TreePath treePath = new TreePath(node.getPath());
    tree.expandPath(treePath);
    if (requestFocus) {
      ActionCallback result = new ActionCallback(2);
      IdeFocusManager.getInstance(project).requestFocus(tree, true).notifyWhenDone(result);
      selectPath(tree, treePath, center).notifyWhenDone(result);
      return result;
    }
    return selectPath(tree, treePath, center);
  }

  @NotNull
  public static List<TreePath> collectSelectedPaths(@NotNull final JTree tree, @NotNull final TreePath treePath) {
    final ArrayList<TreePath> result = new ArrayList<>();
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

  public static void unselect(@NotNull JTree tree, @NotNull final DefaultMutableTreeNode node) {
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

  @Nullable
  public static Range<Integer> getExpandControlRange(@NotNull final JTree aTree, @Nullable final TreePath path) {
    TreeModel treeModel = aTree.getModel();

    final BasicTreeUI basicTreeUI = (BasicTreeUI)aTree.getUI();
    Icon expandedIcon = basicTreeUI.getExpandedIcon();


    Range<Integer> box = null;
    if (path != null && !treeModel.isLeaf(path.getLastPathComponent())) {
      int boxWidth;
      Insets i = aTree.getInsets();

      if (expandedIcon != null) {
        boxWidth = expandedIcon.getIconWidth();
      }
      else {
        boxWidth = 8;
      }

      int boxLeftX = i != null ? i.left : 0;

      boolean leftToRight = aTree.getComponentOrientation().isLeftToRight();
      int depthOffset = getDepthOffset(aTree);
      int totalChildIndent = basicTreeUI.getLeftChildIndent() + basicTreeUI.getRightChildIndent();

      if (leftToRight) {
        boxLeftX += (path.getPathCount() + depthOffset - 2) * totalChildIndent + basicTreeUI.getLeftChildIndent() -
            boxWidth / 2;
      }
      int boxRightX = boxLeftX + boxWidth;

      box = new Range<>(boxLeftX, boxRightX);
    }
    return box;
  }

  public static int getDepthOffset(@NotNull JTree aTree) {
    if (aTree.isRootVisible()) {
      return aTree.getShowsRootHandles() ? 1 : 0;
    }
    else {
      return aTree.getShowsRootHandles() ? 0 : -1;
    }
  }

  @NotNull
  public static RelativePoint getPointForSelection(@NotNull JTree aTree) {
    final int[] rows = aTree.getSelectionRows();
    if (rows == null || rows.length == 0) {
      return RelativePoint.getCenterOf(aTree);
    }
    return getPointForRow(aTree, rows[rows.length - 1]);
  }

  @NotNull
  public static RelativePoint getPointForRow(@NotNull JTree aTree, int aRow) {
    return getPointForPath(aTree, aTree.getPathForRow(aRow));
  }

  @NotNull
  public static RelativePoint getPointForPath(@NotNull JTree aTree, TreePath path) {
    final Rectangle rowBounds = aTree.getPathBounds(path);
    rowBounds.x += 20;
    return getPointForBounds(aTree, rowBounds);
  }

  @NotNull
  public static RelativePoint getPointForBounds(JComponent aComponent, @NotNull final Rectangle aBounds) {
    return new RelativePoint(aComponent, new Point(aBounds.x, (int)aBounds.getMaxY()));
  }

  public static boolean isOverSelection(@NotNull final JTree tree, @NotNull final Point point) {
    TreePath path = tree.getPathForLocation(point.x, point.y);
    return path != null && tree.getSelectionModel().isPathSelected(path);
  }

  public static void dropSelectionButUnderPoint(@NotNull JTree tree, @NotNull Point treePoint) {
    final TreePath toRetain = tree.getPathForLocation(treePoint.x, treePoint.y);
    if (toRetain == null) return;

    TreePath[] selection = tree.getSelectionModel().getSelectionPaths();
    selection = selection == null ? new TreePath[0] : selection;
    for (TreePath each : selection) {
      if (toRetain.equals(each)) continue;
      tree.getSelectionModel().removeSelectionPath(each);
    }
  }

  public interface Traverse{
    boolean accept(Object node);
  }

  public static void ensureSelection(@NotNull JTree tree) {
    final TreePath[] paths = tree.getSelectionPaths();

    if (paths != null) {
      for (TreePath each : paths) {
        if (tree.getRowForPath(each) >= 0 && tree.isVisible(each)) {
          return;
        }
      }
    }

    for (int eachRow = 0; eachRow < tree.getRowCount(); eachRow++) {
      TreePath eachPath = tree.getPathForRow(eachRow);
      if (eachPath != null && tree.isVisible(eachPath)) {
        tree.setSelectionPath(eachPath);
        break;
      }
    }
  }

  public static int indexedBinarySearch(@NotNull TreeNode parent, @NotNull TreeNode key, Comparator comparator) {
    int low = 0;
    int high = parent.getChildCount() - 1;

    while (low <= high) {
      int mid = (low + high) / 2;
      TreeNode treeNode = parent.getChildAt(mid);
      int cmp = comparator.compare(treeNode, key);
      if (cmp < 0) {
        low = mid + 1;
      }
      else if (cmp > 0) {
        high = mid - 1;
      }
      else {
        return mid; // key found
      }
    }
    return -(low + 1);  // key not found
  }

  @NotNull
  public static Comparator<TreePath> getDisplayOrderComparator(@NotNull final JTree tree) {
    return (path1, path2) -> tree.getRowForPath(path1) - tree.getRowForPath(path2);
  }
}
