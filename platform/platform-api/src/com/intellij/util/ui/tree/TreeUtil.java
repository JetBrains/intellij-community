// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Range;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.util.ReflectionUtil.getDeclaredMethod;
import static java.util.stream.Collectors.toList;

public final class TreeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.tree.TreeUtil");
  private static final String TREE_UTIL_SCROLL_TIME_STAMP = "TreeUtil.scrollTimeStamp";
  private static final JBIterable<Integer> NUMBERS = JBIterable.generate(0, i -> i + 1);

  private TreeUtil() {}

  @NotNull
  public static JBTreeTraverser<Object> treeTraverser(@NotNull JTree tree) {
    TreeModel model = tree.getModel();
    Object root = model.getRoot();
    return JBTreeTraverser.from(node -> nodeChildren(node, model)).withRoot(root);
  }

  @NotNull
  public static JBTreeTraverser<TreePath> treePathTraverser(@NotNull JTree tree) {
    TreeModel model = tree.getModel();
    Object root = model.getRoot();
    TreePath rootPath = root == null ? null : new TreePath(root);
    return JBTreeTraverser.<TreePath>from(path -> nodeChildren(path.getLastPathComponent(), model)
      .map(o -> path.pathByAddingChild(o)))
      .withRoot(rootPath);
  }

  @NotNull
  public static JBIterable<Object> nodeChildren(@Nullable Object node, @NotNull TreeModel model) {
    int count = model.getChildCount(node);
    return count == 0 ? JBIterable.empty() : NUMBERS.take(count).map(index -> model.getChild(node, index));
  }

  @NotNull
  public static JBTreeTraverser<TreeNode> treeNodeTraverser(@Nullable TreeNode treeNode) {
    return JBTreeTraverser.<TreeNode>from(node -> nodeChildren(node)).withRoot(treeNode);
  }

  @NotNull
  public static JBIterable<TreeNode> nodeChildren(@Nullable TreeNode treeNode) {
    int count = treeNode == null ? 0 : treeNode.getChildCount();
    return count == 0 ? JBIterable.empty() : NUMBERS.take(count).map(index -> treeNode.getChildAt(index));
  }

  /**
   * @param tree a tree, which viewable paths are processed
   * @return a list of expanded paths
   */
  @NotNull
  public static List<TreePath> collectExpandedPaths(@NotNull JTree tree) {
    return collectExpandedObjects(tree, Function.identity());
  }

  /**
   * @param tree a tree, which viewable paths are processed
   * @return a list of user objects which correspond to expanded paths under the specified root node
   */
  @NotNull
  public static List<Object> collectExpandedUserObjects(@NotNull JTree tree) {
    return collectExpandedObjects(tree, TreeUtil::getLastUserObject);
  }

  /**
   * @param tree   a tree, which viewable paths are processed
   * @param mapper a function to convert a expanded tree path to a corresponding object
   * @return a list of objects which correspond to expanded paths under the specified root node
   */
  @NotNull
  public static <T> List<T> collectExpandedObjects(@NotNull JTree tree, @NotNull Function<? super TreePath, ? extends T> mapper) {
    return collectVisibleRows(tree, tree::isExpanded, mapper);
  }

  @Nullable
  public static <T> T findObjectInPath(@Nullable TreePath path, @NotNull Class<T> clazz) {
    while (path != null) {
      T object = getLastUserObject(clazz, path);
      if (object != null) return object;
      path = path.getParentPath();
    }
    return null;
  }

  /**
   * @param tree a tree, which selection is processed
   * @param type a {@code Class} object to filter selected user objects
   * @return a list of user objects of the specified type retrieved from all selected paths
   */
  @NotNull
  public static <T> List<T> collectSelectedObjectsOfType(@NotNull JTree tree, @NotNull Class<? extends T> type) {
    return collectSelectedObjects(tree, path -> getLastUserObject(type, path));
  }

  /**
   * @param tree a tree, which viewable paths are processed
   * @param root an ascendant tree path to filter expanded tree paths
   * @return a list of expanded paths under the specified root node
   */
  @NotNull
  public static List<TreePath> collectExpandedPaths(@NotNull JTree tree, @NotNull TreePath root) {
    return collectExpandedObjects(tree, root, Function.identity());
  }

  /**
   * @param tree a tree, which viewable paths are processed
   * @param root an ascendant tree path to filter expanded tree paths
   * @return a list of user objects which correspond to expanded paths under the specified root node
   */
  @NotNull
  public static List<Object> collectExpandedUserObjects(@NotNull JTree tree, @NotNull TreePath root) {
    return collectExpandedObjects(tree, root, TreeUtil::getLastUserObject);
  }

  /**
   * @param tree   a tree, which viewable paths are processed
   * @param root   an ascendant tree path to filter expanded tree paths
   * @param mapper a function to convert a expanded tree path to a corresponding object
   * @return a list of objects which correspond to expanded paths under the specified root node
   */
  @NotNull
  public static <T> List<T> collectExpandedObjects(@NotNull JTree tree, @NotNull TreePath root, @NotNull Function<? super TreePath, ? extends T> mapper) {
    if (!tree.isVisible(root)) return Collections.emptyList(); // invisible path should not be expanded
    return collectVisibleRows(tree, path -> tree.isExpanded(path) && root.isDescendant(path), mapper);
  }

  /**
   * Expands specified paths.
   * @param tree JTree to apply expansion status to
   * @param paths to expand. See {@link #collectExpandedPaths(JTree, TreePath)}
   */
  public static void restoreExpandedPaths(@NotNull final JTree tree, @NotNull final List<? extends TreePath> paths){
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
   *
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
    return findNode(aRoot, node -> Comparing.equal(node.getUserObject(), aObject));
  }

  @Nullable
  public static DefaultMutableTreeNode findNode(@NotNull final DefaultMutableTreeNode aRoot,
                                                @NotNull final Condition<? super DefaultMutableTreeNode> condition) {
    if (condition.value(aRoot)) {
      return aRoot;
    } else {
      for (int i = 0; i < aRoot.getChildCount(); i++) {
        final DefaultMutableTreeNode candidate = findNode((DefaultMutableTreeNode)aRoot.getChildAt(i), condition);
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

  /**
   * Tries to select the first node in the specified tree as soon as possible.
   *
   * @param tree a tree, which node should be selected
   * @return a callback that will be done when first visible node is selected
   * @see #promiseSelectFirst
   */
  @NotNull
  public static ActionCallback selectFirstNode(@NotNull JTree tree) {
    return Promises.toActionCallback(promiseSelectFirst(tree));
  }

  @NotNull
  public static TreePath getFirstNodePath(@NotNull JTree tree) {
    TreeModel model = tree.getModel();
    Object root = model.getRoot();
    TreePath selectionPath = new TreePath(root);
    if (!tree.isRootVisible() && model.getChildCount(root) > 0) {
      selectionPath = selectionPath.pathByAddingChild(model.getChild(root, 0));
    }
    return selectionPath;
  }

  /**
   * @deprecated use {@link #promiseSelectFirstLeaf}
   */
  @Deprecated
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
    return result.toArray(new TreePath[0]);
  }

  @NotNull
  public static TreePath[] selectMaximals(@Nullable final TreePath[] paths) {
    if (paths == null) return new TreePath[0];
    final TreePath[] noDuplicates = removeDuplicates(paths);
    final ArrayList<TreePath> result = new ArrayList<>();
    for (final TreePath path : noDuplicates) {
      final ArrayList<TreePath> otherPaths = new ArrayList<>(Arrays.asList(noDuplicates));
      otherPaths.remove(path);
      if (!isDescendants(path, otherPaths.toArray(new TreePath[0]))) result.add(path);
    }
    return result.toArray(new TreePath[0]);
  }

  public static void sort(@NotNull final DefaultTreeModel model, @Nullable Comparator comparator) {
    sort((DefaultMutableTreeNode) model.getRoot(), comparator);
  }

  public static void sort(@NotNull final DefaultMutableTreeNode node, @Nullable Comparator comparator) {
    sortRecursively(node, comparator);
  }

  public static <T extends MutableTreeNode> void sortRecursively(@NotNull T node, @Nullable Comparator<? super T> comparator) {
    sortChildren(node, comparator);
    for (int i = 0; i < node.getChildCount(); i++) {
      //noinspection unchecked
      sortRecursively((T) node.getChildAt(i), comparator);
    }
  }

  public static <T extends MutableTreeNode> void sortChildren(@NotNull T node, @Nullable Comparator<? super T> comparator) {
    //noinspection unchecked
    final List<T> children = (List)listChildren(node);
    Collections.sort(children, comparator);
    for (int i = node.getChildCount() - 1; i >= 0; i--) {
      node.remove(i);
    }
    addChildrenTo(node, children);
  }

  public static void addChildrenTo(@NotNull final MutableTreeNode node, @NotNull final List<? extends TreeNode> children) {
    for (final Object aChildren : children) {
      final MutableTreeNode child = (MutableTreeNode)aChildren;
      node.insert(child, node.getChildCount());
    }
  }

  /** @deprecated use TreeUtil#treeTraverser() or TreeUtil#treeNodeTraverser() directly */
  @Deprecated
  public static boolean traverse(@NotNull TreeNode node, @NotNull Traverse traverse) {
    return treeNodeTraverser(node).traverse(TreeTraversal.POST_ORDER_DFS).processEach(traverse::accept);
  }

  /** @deprecated use TreeUtil#treeTraverser() or TreeUtil#treeNodeTraverser() directly */
  @Deprecated
  public static boolean traverseDepth(@NotNull TreeNode node, @NotNull Traverse traverse) {
    return treeNodeTraverser(node).traverse(TreeTraversal.PRE_ORDER_DFS).processEach(traverse::accept);
  }

  public static void selectPaths(@NotNull JTree tree, @NotNull Collection<? extends TreePath> paths) {
    if (paths.isEmpty()) return;
    selectPaths(tree, paths.toArray(new TreePath[0]));
  }

  public static void selectPaths(@NotNull JTree tree, @NotNull TreePath... paths) {
    if (paths.length == 0) return;
    for (TreePath path : paths) {
      tree.makeVisible(path);
    }
    tree.setSelectionPaths(paths);
    tree.scrollPathToVisible(paths[0]);
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


    if (!okToScroll || !scroll) {
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
      selectRunnable.run();
      return ActionCallback.DONE;
    }
    final Component comp =
      tree.getCellRenderer().getTreeCellRendererComponent(tree, path.getLastPathComponent(), true, true, false, row, false);

    if (comp instanceof SimpleColoredComponent) {
      final SimpleColoredComponent renderer = (SimpleColoredComponent)comp;
      final Dimension scrollableSize = renderer.computePreferredSize(true);
      bounds.width = scrollableSize.width;
    }

    final ActionCallback callback = new ActionCallback();


    selectRunnable.run();

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
        bounds.x = tree.getVisibleRect().x;
      }

    LOG.debug("tree scroll: ", path);
    tree.scrollRectToVisible(bounds);
    // try to scroll later when the tree is ready
    Object property = tree.getClientProperty(TREE_UTIL_SCROLL_TIME_STAMP);
    long stamp = property instanceof Long ? (Long)property + 1L : Long.MIN_VALUE;
    tree.putClientProperty(TREE_UTIL_SCROLL_TIME_STAMP, stamp);
    // store relative offset because the row can be moved during the tree updating
    int offset = rowBounds.y - bounds.y;

    AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(tree);
    scrollToVisible(tree, path, bounds, offset, stamp, callback::setDone, builder, 3);

    return callback;
  }

  private static void scrollToVisible(JTree tree, TreePath path, Rectangle bounds, int offset, long expected, Runnable done,
                                      AbstractTreeBuilder builder, int attempt) {
    Runnable scroll = () -> {
      Rectangle pathBounds = attempt <= 0 ? null : tree.getPathBounds(path);
      if (pathBounds != null) {
        Object property = tree.getClientProperty(TREE_UTIL_SCROLL_TIME_STAMP);
        long stamp = property instanceof Long ? (Long)property : Long.MAX_VALUE;
        LOG.debug("tree scroll ", attempt, stamp == expected ? ": try again: " : ": ignore: ", path);
        if (stamp == expected) {
          bounds.y = pathBounds.y - offset; // restore bounds according to the current row
          Rectangle visible = tree.getVisibleRect();
          if (bounds.y < visible.y || bounds.y > visible.y + Math.max(0, visible.height - bounds.height)) {
            tree.scrollRectToVisible(bounds);
            scrollToVisible(tree, path, bounds, offset, expected, done, builder, attempt - 1);
            return; // try to scroll again
          }
        }
      }
      done.run();
    };
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(builder == null ? scroll : () -> builder.getReady(TreeUtil.class).doWhenDone(scroll));
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

  @SuppressWarnings("HardCodedStringLiteral")
  public static void installActions(@NotNull final JTree tree) {
    TreeUI ui = tree.getUI();
    if (ui != null && ui.getClass().getName().equals("com.intellij.ui.tree.ui.DefaultTreeUI")) return;
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

  public static void collapseAll(@NotNull JTree tree, final int keepSelectionLevel) {
    final TreePath leadSelectionPath = tree.getLeadSelectionPath();
    // Collapse all
    int row = tree.getRowCount() - 1;
    while (row >= 0) {
      tree.collapseRow(row);
      row--;
    }
    Object root = tree.getModel().getRoot();
    if (root != null && !tree.isRootVisible()) {
      tree.expandPath(new TreePath(root));
    }
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
  public static List<TreeNode> listChildren(@NotNull final TreeNode node) {
    //ApplicationManager.getApplication().assertIsDispatchThread();
    int size = node.getChildCount();
    ArrayList<TreeNode> result = new ArrayList<>(size);
    for(int i = 0; i < size; i++){
      TreeNode child = node.getChildAt(i);
      LOG.assertTrue(child != null);
      result.add(child);
    }
    return result;
  }

  public static void expandRootChildIfOnlyOne(@Nullable final JTree tree) {
    if (tree == null) return;
    Runnable runnable = () -> {
      TreeModel model = tree.getModel();
      Object root = model.getRoot();
      if (root == null) return;
      TreePath rootPath = new TreePath(root);
      tree.expandPath(rootPath);
      if (model.getChildCount(root) == 1) {
        Object firstChild = model.getChild(root, 0);
        tree.expandPath(rootPath.pathByAddingChild(firstChild));
      }
    };
    UIUtil.invokeLaterIfNeeded(runnable);
  }

  public static void expandAll(@NotNull JTree tree) {
    promiseExpandAll(tree);
  }

  /**
   * Expands all nodes in the specified tree and runs the specified task on done.
   *
   * @param tree   a tree, which nodes should be expanded
   * @param onDone a task to run on EDT after expanding nodes
   */
  public static void expandAll(@NotNull JTree tree, @NotNull Runnable onDone) {
    promiseExpandAll(tree).onSuccess(result -> UIUtil.invokeLaterIfNeeded(onDone));
  }

  /**
   * Promises to expand all nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree a tree, which nodes should be expanded
   * @return a promise that will be succeed when all nodes are expanded
   */
  @NotNull
  public static Promise<?> promiseExpandAll(@NotNull JTree tree) {
    return promiseExpand(tree, Integer.MAX_VALUE);
  }

  /**
   * Expands n levels of the tree counting from the root
   * @param tree to expand nodes of
   * @param levels depths of the expantion
   */
  public static void expand(@NotNull JTree tree, int levels) {
    promiseExpand(tree, levels);
  }

  /**
   * Expands some nodes in the specified tree and runs the specified task on done.
   *
   * @param tree   a tree, which nodes should be expanded
   * @param depth  a depth starting from the root node
   * @param onDone a task to run on EDT after expanding nodes
   */
  public static void expand(@NotNull JTree tree, int depth, @NotNull Runnable onDone) {
    promiseExpand(tree, depth).onSuccess(result -> UIUtil.invokeLaterIfNeeded(onDone));
  }

  /**
   * Promises to expand some nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree  a tree, which nodes should be expanded
   * @param depth a depth starting from the root node
   * @return a promise that will be succeed when all needed nodes are expanded
   */
  @NotNull
  public static Promise<?> promiseExpand(@NotNull JTree tree, int depth) {
    AsyncPromise<?> promise = new AsyncPromise<>();
    promiseMakeVisible(tree, path -> depth < path.getPathCount() ? TreeVisitor.Action.SKIP_SIBLINGS : TreeVisitor.Action.CONTINUE, promise)
      .onError(promise::setError)
      .onSuccess(path -> {
        if (promise.isCancelled()) return;
        promise.setResult(null);
      });
    return promise;
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
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(tree, true));
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

  /**
   * Returns {@code true} if the node identified by the {@code path} is currently viewable in the {@code tree}.
   * The difference from the {@link JTree#isVisible(TreePath)} method is that this method
   * returns {@code false} for the hidden root node, when {@link JTree#isRootVisible()} returns {@code false}.
   *
   * @param tree a tree, to which the given path belongs
   * @param path a path whose visibility in the given tree is checking
   * @return {@code true} if {@code path} is viewable in {@code tree}
   * @see JTree#isRootVisible()
   * @see JTree#isVisible(TreePath)
   */
  private static boolean isViewable(@NotNull JTree tree, @NotNull TreePath path) {
    TreePath parent = path.getParentPath();
    return parent != null ? tree.isExpanded(parent) : tree.isRootVisible();
  }

  /**
   * @param tree a tree, which selection is processed
   * @return a list of all selected paths
   */
  @NotNull
  public static List<TreePath> collectSelectedPaths(@NotNull JTree tree) {
    return collectSelectedObjects(tree, Function.identity());
  }

  /**
   * @param tree a tree, which selection is processed
   * @return a list of user objects which correspond to all selected paths
   */
  @NotNull
  public static List<Object> collectSelectedUserObjects(@NotNull JTree tree) {
    return collectSelectedObjects(tree, TreeUtil::getLastUserObject);
  }

  /**
   * @param tree   a tree, which selection is processed
   * @param mapper a function to convert a selected tree path to a corresponding object
   * @return a list of objects which correspond to all selected paths
   */
  @NotNull
  public static <T> List<T> collectSelectedObjects(@NotNull JTree tree, @NotNull Function<? super TreePath, ? extends T> mapper) {
    return getSelection(tree, path -> isViewable(tree, path), mapper);
  }

  /**
   * @param tree a tree, which selection is processed
   * @param root an ascendant tree path to filter selected tree paths
   * @return a list of selected paths under the specified root node
   */
  @NotNull
  public static List<TreePath> collectSelectedPaths(@NotNull JTree tree, @NotNull TreePath root) {
    return collectSelectedObjects(tree, root, Function.identity());
  }

  /**
   * @param tree a tree, which selection is processed
   * @param root an ascendant tree path to filter selected tree paths
   * @return a list of user objects which correspond to selected paths under the specified root node
   */
  @NotNull
  public static List<Object> collectSelectedUserObjects(@NotNull JTree tree, @NotNull TreePath root) {
    return collectSelectedObjects(tree, root, TreeUtil::getLastUserObject);
  }

  /**
   * @param tree   a tree, which selection is processed
   * @param root   an ascendant tree path to filter selected tree paths
   * @param mapper a function to convert a selected tree path to a corresponding object
   * @return a list of objects which correspond to selected paths under the specified root node
   */
  @NotNull
  public static <T> List<T> collectSelectedObjects(@NotNull JTree tree, @NotNull TreePath root, @NotNull Function<? super TreePath, ? extends T> mapper) {
    if (!tree.isVisible(root)) return Collections.emptyList(); // invisible path should not be selected
    return getSelection(tree, path -> isViewable(tree, path) && root.isDescendant(path), mapper);
  }

  @NotNull
  private static <T> List<T> getSelection(@NotNull JTree tree, @NotNull Predicate<? super TreePath> filter, @NotNull Function<? super TreePath, ? extends T> mapper) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length == 0) return Collections.emptyList(); // nothing is selected
    return Stream.of(paths).filter(filter).map(mapper).filter(Objects::nonNull).collect(toList());
  }

  public static void unselectPath(@NotNull JTree tree, @Nullable TreePath path) {
    if (path == null) return;
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths == null) return;

    for (TreePath selectionPath : selectionPaths) {
      if (selectionPath.getPathCount() > path.getPathCount() && path.isDescendant(selectionPath)) {
        tree.removeSelectionPath(selectionPath);
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

      boxWidth = expandedIcon != null ? expandedIcon.getIconWidth() : 8;

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

  public static int getNodeDepth(@NotNull JTree tree, @NotNull TreePath path) {
    int depth = path.getPathCount();
    if (!tree.isRootVisible()) depth--;
    if (!tree.getShowsRootHandles()) depth--;
    return depth;
  }

  private static final class LazyRowX {
    static final Method METHOD = getDeclaredMethod(BasicTreeUI.class, "getRowX", int.class, int.class);
  }

  @Deprecated
  public static int getNodeRowX(@NotNull JTree tree, int row) {
    if (LazyRowX.METHOD == null) return -1; // system error
    TreePath path = tree.getPathForRow(row);
    if (path == null) return -1; // path does not exist
    int depth = getNodeDepth(tree, path);
    if (depth < 0) return -1; // root is not visible
    try {
      return (Integer)LazyRowX.METHOD.invoke(tree.getUI(), row, depth);
    }
    catch (Exception exception) {
      LOG.error(exception);
      return -1; // unexpected
    }
  }

  private static final class LazyLocationInExpandControl {
    static final Method METHOD = getDeclaredMethod(BasicTreeUI.class, "isLocationInExpandControl", TreePath.class, int.class, int.class);
  }

  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static boolean isLocationInExpandControl(@NotNull JTree tree, int x, int y) {
    if (LazyLocationInExpandControl.METHOD == null) return false; // system error
    return isLocationInExpandControl(tree, tree.getClosestPathForLocation(x, y), x, y);
  }

  @Deprecated
  public static boolean isLocationInExpandControl(@NotNull JTree tree, @Nullable TreePath path, int x, int y) {
    if (LazyLocationInExpandControl.METHOD == null || path == null) return false; // system error or undefined path
    try {
      return (Boolean)LazyLocationInExpandControl.METHOD.invoke(tree.getUI(), path, x, y);
    }
    catch (Exception exception) {
      LOG.error(exception);
      return false; // unexpected
    }
  }

  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static void invalidateCacheAndRepaint(@Nullable TreeUI ui) {
    if (ui instanceof BasicTreeUI) {
      BasicTreeUI basic = (BasicTreeUI)ui;
      basic.setLeftChildIndent(basic.getLeftChildIndent());
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

  @Nullable
  public static Object getUserObject(@Nullable Object node) {
    return node instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode)node).getUserObject() : node;
  }

  @Nullable
  public static <T> T getUserObject(@NotNull Class<T> type, @Nullable Object node) {
    node = getUserObject(node);
    return type.isInstance(node) ? type.cast(node) : null;
  }

  /**
   * @return an user object retrieved from the last component of the specified {@code path}
   */
  @Nullable
  public static Object getLastUserObject(@Nullable TreePath path) {
    return path == null ? null : getUserObject(path.getLastPathComponent());
  }

  @Nullable
  public static <T> T getLastUserObject(@NotNull Class<T> type, @Nullable TreePath path) {
    return path == null ? null : getUserObject(type, path.getLastPathComponent());
  }

  @Nullable
  public static TreePath getSelectedPathIfOne(@NotNull JTree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    return paths != null && paths.length == 1 ? paths[0] : null;
  }

  /** @deprecated use TreeUtil#treePathTraverser() */
  @Deprecated
  @FunctionalInterface
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

  public static <T extends MutableTreeNode> void insertNode(@NotNull T child, @NotNull T parent, @Nullable DefaultTreeModel model,
                                                            @NotNull Comparator<? super T> comparator) {
    insertNode(child, parent, model, false, comparator);
  }

  public static <T extends MutableTreeNode> void insertNode(@NotNull T child, @NotNull T parent, @Nullable DefaultTreeModel model,
                                                            boolean allowDuplication, @NotNull Comparator<? super T> comparator) {
    int index = indexedBinarySearch(parent, child, comparator);
    if (index >= 0 && !allowDuplication) {
      LOG.error("Node " + child + " is already added to " + parent);
      return;
    }
    int insertionPoint = index >= 0 ? index : -(index + 1);
    if (model != null) {
      model.insertNodeInto(child, parent, insertionPoint);
    }
    else {
      parent.insert(child, insertionPoint);
    }
  }

  public static <T extends TreeNode> int indexedBinarySearch(@NotNull T parent, @NotNull T key, @NotNull Comparator<? super T> comparator) {
    return ObjectUtils.binarySearch(0, parent.getChildCount(), mid -> comparator.compare((T)parent.getChildAt(mid), key));
  }

  @NotNull
  public static Comparator<TreePath> getDisplayOrderComparator(@NotNull final JTree tree) {
    return Comparator.comparingInt(tree::getRowForPath);
  }

  private static void expandPathWithDebug(@NotNull JTree tree, @NotNull TreePath path) {
    LOG.debug("tree expand path: ", path);
    tree.expandPath(path);
  }

  /**
   * Expands a node in the specified tree.
   *
   * @param tree     a tree, which nodes should be expanded
   * @param visitor  a visitor that controls expanding of tree nodes
   * @param consumer a path consumer called on EDT if path is found and expanded
   */
  public static void expand(@NotNull JTree tree, @NotNull TreeVisitor visitor, @NotNull Consumer<? super TreePath> consumer) {
    promiseMakeVisibleOne(tree, visitor, path -> {
      expandPathWithDebug(tree, path);
      consumer.accept(path);
    });
  }

  /**
   * Promises to expand a node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree    a tree, which nodes should be expanded
   * @param visitor a visitor that controls expanding of tree nodes
   * @return a promise that will be succeed only if path is found and expanded
   */
  @NotNull
  public static Promise<TreePath> promiseExpand(@NotNull JTree tree, @NotNull TreeVisitor visitor) {
    return promiseMakeVisibleOne(tree, visitor, path -> expandPathWithDebug(tree, path));
  }

  /**
   * Promises to expand several nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree     a tree, which nodes should be expanded
   * @param visitors visitors to control expanding of tree nodes
   * @return a promise that will be succeed only if paths are found and expanded
   */
  @NotNull
  public static Promise<List<TreePath>> promiseExpand(@NotNull JTree tree, @NotNull Stream<? extends TreeVisitor> visitors) {
    return promiseMakeVisibleAll(tree, visitors, paths -> paths.forEach(path -> expandPathWithDebug(tree, path)));
  }

  /**
   * Makes visible a node in the specified tree.
   *
   * @param tree     a tree, which nodes should be made visible
   * @param visitor  a visitor that controls expanding of tree nodes
   * @param consumer a path consumer called on EDT if path is found and made visible
   */
  public static void makeVisible(@NotNull JTree tree, @NotNull TreeVisitor visitor, @NotNull Consumer<? super TreePath> consumer) {
    promiseMakeVisibleOne(tree, visitor, consumer);
  }

  /**
   * Promises to make visible a node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree    a tree, which nodes should be made visible
   * @param visitor a visitor that controls expanding of tree nodes
   * @return a promise that will be succeed only if path is found and made visible
   */
  @NotNull
  public static Promise<TreePath> promiseMakeVisible(@NotNull JTree tree, @NotNull TreeVisitor visitor) {
    return promiseMakeVisibleOne(tree, visitor, null);
  }

  @NotNull
  private static Promise<TreePath> promiseMakeVisibleOne(@NotNull JTree tree,
                                                         @NotNull TreeVisitor visitor,
                                                         @Nullable Consumer<? super TreePath> consumer) {
    AsyncPromise<TreePath> promise = new AsyncPromise<>();
    promiseMakeVisible(tree, visitor, promise)
      .onError(promise::setError)
      .onSuccess(path -> {
        if (promise.isCancelled()) return;
        UIUtil.invokeLaterIfNeeded(() -> {
          if (promise.isCancelled()) return;
          if (tree.isVisible(path)) {
            if (consumer != null) consumer.accept(path);
            promise.setResult(path);
          }
          else {
            promise.cancel();
          }
        });
      });
    return promise;
  }

  /**
   * Promises to make visible several nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree     a tree, which nodes should be made visible
   * @param visitors visitors to control expanding of tree nodes
   * @return a promise that will be succeed only if path are found and made visible
   */
  @NotNull
  public static Promise<List<TreePath>> promiseMakeVisible(@NotNull JTree tree, @NotNull Stream<? extends TreeVisitor> visitors) {
    return promiseMakeVisibleAll(tree, visitors, null);
  }

  private static Promise<List<TreePath>> promiseMakeVisibleAll(@NotNull JTree tree,
                                                               @NotNull Stream<? extends TreeVisitor> visitors,
                                                               @Nullable Consumer<? super List<TreePath>> consumer) {
    AsyncPromise<List<TreePath>> promise = new AsyncPromise<>();
    List<Promise<TreePath>> promises = visitors
      .filter(Objects::nonNull)
      .map(visitor -> promiseMakeVisible(tree, visitor, promise))
      .collect(toList());
    Promises.collectResults(promises, true)
      .onError(promise::setError)
      .onSuccess(paths -> {
        if (promise.isCancelled()) return;
        if (!ContainerUtil.isEmpty(paths)) {
          UIUtil.invokeLaterIfNeeded(() -> {
            if (promise.isCancelled()) return;
            List<TreePath> visible = ContainerUtil.filter(paths, tree::isVisible);
            if (!ContainerUtil.isEmpty(visible)) {
              if (consumer != null) consumer.accept(visible);
              promise.setResult(visible);
            }
            else {
              promise.cancel();
            }
          });
        }
        else {
          promise.cancel();
        }
      });
    return promise;
  }

  @NotNull
  private static Promise<TreePath> promiseMakeVisible(@NotNull JTree tree, @NotNull TreeVisitor visitor, @NotNull AsyncPromise<?> promise) {
    return promiseVisit(tree, path -> {
      if (promise.isCancelled()) return TreeVisitor.Action.SKIP_SIBLINGS;
      TreeVisitor.Action action = visitor.visit(path);
      if (action == TreeVisitor.Action.CONTINUE || action == TreeVisitor.Action.INTERRUPT) {
        // do not expand children if parent path is collapsed
        if (!tree.isVisible(path)) {
          if (!promise.isCancelled()) {
            LOG.debug("tree expand canceled");
            promise.cancel();
          }
          return TreeVisitor.Action.SKIP_SIBLINGS;
        }
        if (action == TreeVisitor.Action.CONTINUE) expandPathWithDebug(tree, path);
      }
      return action;
    });
  }

  /**
   * Selects a node in the specified tree.
   *
   * @param tree     a tree, which nodes should be selected
   * @param visitor  a visitor that controls expanding of tree nodes
   * @param consumer a path consumer called on EDT if path is found and selected
   */
  public static void select(@NotNull JTree tree, @NotNull TreeVisitor visitor, @NotNull Consumer<? super TreePath> consumer) {
    promiseMakeVisibleOne(tree, visitor, path -> {
      internalSelectPath(tree, path);
      consumer.accept(path);
    });
  }

  /**
   * Promises to select a node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree    a tree, which nodes should be selected
   * @param visitor a visitor that controls expanding of tree nodes
   * @return a promise that will be succeed only if path is found and selected
   */
  @NotNull
  public static Promise<TreePath> promiseSelect(@NotNull JTree tree, @NotNull TreeVisitor visitor) {
    return promiseMakeVisibleOne(tree, visitor, path -> internalSelectPath(tree, path));
  }

  private static void internalSelectPath(@NotNull JTree tree, @NotNull TreePath path) {
    assert EventQueue.isDispatchThread();
    tree.setSelectionPath(path);
    scrollToVisible(tree, path, true);
  }

  /**
   * Promises to select several nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree     a tree, which nodes should be selected
   * @param visitors visitors to control expanding of tree nodes
   * @return a promise that will be succeed only if paths are found and selected
   */
  @NotNull
  public static Promise<List<TreePath>> promiseSelect(@NotNull JTree tree, @NotNull Stream<? extends TreeVisitor> visitors) {
    return promiseMakeVisibleAll(tree, visitors, paths -> internalSelectPaths(tree, paths));
  }

  private static void internalSelectPaths(@NotNull JTree tree, @NotNull List<? extends TreePath> paths) {
    assert EventQueue.isDispatchThread();
    if (paths.isEmpty()) return;
    tree.setSelectionPaths(paths.toArray(new TreePath[0]));
    for (TreePath path : paths) {
      if (scrollToVisible(tree, path, true)) {
        break;
      }
    }
  }

  /**
   * @param tree     a tree to scroll
   * @param path     a visible tree path to scroll
   * @param centered {@code true} to show the specified path
   * @return {@code false} if a path is hidden (under a collapsed parent)
   */
  @Contract("_, null, _ -> false")
  public static boolean scrollToVisible(@NotNull JTree tree, @NotNull TreePath path, boolean centered) {
    assert EventQueue.isDispatchThread();
    Rectangle bounds = tree.getPathBounds(path);
    if (bounds == null) {
      LOG.debug("cannot scroll to: ", path);
      return false;
    }
    Container parent = tree.getParent();
    if (parent instanceof JViewport) {
      int width = parent.getWidth();
      if (!centered && tree instanceof Tree && !((Tree)tree).isHorizontalAutoScrollingEnabled()) {
        bounds.x = -tree.getX();
        bounds.width = width;
      }
      else {
        bounds.width = Math.min(bounds.width, width / 2);
        bounds.x -= JBUIScale.scale(20); // TODO: calculate a control width
        if (bounds.x < 0) {
          bounds.width += bounds.x;
          bounds.x = 0;
        }
      }
      int height = parent.getHeight();
      if (height > bounds.height && height < tree.getHeight()) {
        if (centered || height < bounds.height * 5) {
          bounds.y -= (height - bounds.height) / 2;
          bounds.height = height;
        }
        else {
          bounds.y -= bounds.height * 2;
          bounds.height *= 5;
        }
        if (bounds.y < 0) {
          bounds.height += bounds.y;
          bounds.y = 0;
        }
        int y = bounds.y + bounds.height - tree.getHeight();
        if (y > 0) bounds.height -= y;
      }
    }
    scrollToVisibleWithAccessibility(tree, bounds);
    return true;
  }

  private static void scrollToVisibleWithAccessibility(@NotNull JTree tree, @NotNull Rectangle bounds) {
    tree.scrollRectToVisible(bounds);
    AccessibleContext context = tree.getAccessibleContext();
    if (context != null) context.firePropertyChange(AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY, false, true);
  }

  /**
   * Promises to select the first node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree a tree, which node should be selected
   * @return a promise that will be succeed when first visible node is selected
   */
  @NotNull
  public static Promise<TreePath> promiseSelectFirst(@NotNull JTree tree) {
    return promiseSelect(tree, path -> !tree.isRootVisible() && path.getParentPath() == null
                                       ? TreeVisitor.Action.CONTINUE
                                       : TreeVisitor.Action.INTERRUPT);
  }

  /**
   * Promises to select the first leaf node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree a tree, which node should be selected
   * @return a promise that will be succeed when first leaf node is made visible and selected
   */
  @NotNull
  public static Promise<TreePath> promiseSelectFirstLeaf(@NotNull JTree tree) {
    AtomicReference<TreePath> reference = new AtomicReference<>();
    AsyncPromise<TreePath> promise = new AsyncPromise<>();
    promiseMakeVisible(tree, path -> {
      TreePath parent = reference.getAndSet(path);
      if (getPathCount(parent) == getPathCount(path.getParentPath())) return TreeVisitor.Action.CONTINUE;
      internalSelectPath(tree, parent);
      promise.setResult(parent);
      return TreeVisitor.Action.INTERRUPT;
    }, promise)
      .onError(promise::setError)
      .onSuccess(path -> {
        if (!promise.isDone()) promise.cancel();
      });
    return promise;
  }

  private static int getPathCount(@Nullable TreePath path) {
    return path == null ? 0 : path.getPathCount();
  }

  /**
   * Processes nodes in the specified tree.
   *
   * @param tree     a tree, which nodes should be processed
   * @param visitor  a visitor that controls processing of tree nodes
   * @param consumer a path consumer called on done
   */
  public static void visit(@NotNull JTree tree, @NotNull TreeVisitor visitor, @NotNull Consumer<? super TreePath> consumer) {
    promiseVisit(tree, visitor).onSuccess(path -> UIUtil.invokeLaterIfNeeded(() -> consumer.accept(path)));
  }

  /**
   * Promises to process nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree    a tree, which nodes should be processed
   * @param visitor a visitor that controls processing of tree nodes
   * @return a promise that will be succeed when visiting is finished
   */
  @NotNull
  public static Promise<TreePath> promiseVisit(@NotNull JTree tree, @NotNull TreeVisitor visitor) {
    TreeModel model = tree.getModel();
    if (model instanceof TreeVisitor.Acceptor) {
      TreeVisitor.Acceptor acceptor = (TreeVisitor.Acceptor)model;
      return acceptor.accept(visitor);
    }
    if (model == null) return Promises.rejectedPromise("tree model is not set");
    AsyncPromise<TreePath> promise = new AsyncPromise<>();
    UIUtil.invokeLaterIfNeeded(() -> promise.setResult(visitModel(model, visitor)));
    return promise;
  }

  /**
   * Processes nodes in the specified tree model.
   *
   * @param model   a tree model, which nodes should be processed
   * @param visitor a visitor that controls processing of tree nodes
   */
  private static TreePath visitModel(@NotNull TreeModel model, @NotNull TreeVisitor visitor) {
    Object root = model.getRoot();
    if (root == null) return null;

    TreePath path = new TreePath(root);
    switch (visitor.visit(path)) {
      case INTERRUPT:
        return path; // root path is found
      case CONTINUE:
        break; // visit children
      default:
        return null; // skip children
    }
    Deque<Deque<TreePath>> stack = new ArrayDeque<>();
    stack.push(children(model, path));
    while (path != null) {
      Deque<TreePath> siblings = stack.peek();
      if (siblings == null) return null; // nothing to process

      TreePath next = siblings.poll();
      if (next == null) {
        LOG.assertTrue(siblings == stack.poll());
        path = path.getParentPath();
      }
      else {
        switch (visitor.visit(next)) {
          case INTERRUPT:
            return next; // path is found
          case CONTINUE:
            path = next;
            stack.push(children(model, path));
            break;
          case SKIP_SIBLINGS:
            siblings.clear();
            break;
          case SKIP_CHILDREN:
            break;
        }
      }
    }
    LOG.assertTrue(stack.isEmpty());
    return null;
  }

  @NotNull
  private static Deque<TreePath> children(@NotNull TreeModel model, @NotNull TreePath path) {
    Object object = path.getLastPathComponent();
    int count = model.getChildCount(object);
    Deque<TreePath> deque = new ArrayDeque<>(count);
    for (int i = 0; i < count; i++) {
      deque.add(path.pathByAddingChild(model.getChild(object, i)));
    }
    return deque;
  }

  /**
   * Processes visible nodes in the specified tree.
   *
   * @param tree    a tree, which nodes should be processed
   * @param visitor a visitor that controls processing of tree nodes
   */
  public static TreePath visitVisibleRows(@NotNull JTree tree, @NotNull TreeVisitor visitor) {
    TreePath parent = null;
    int count = tree.getRowCount();
    for (int row = 0; row < count; row++) {
      if (count != tree.getRowCount()) {
        throw new ConcurrentModificationException("tree is modified");
      }
      TreePath path = tree.getPathForRow(row);
      if (path == null) {
        throw new NullPointerException("path is not found at row " + row);
      }
      if (parent == null || !parent.isDescendant(path)) {
        switch (visitor.visit(path)) {
          case INTERRUPT:
            return path; // path is found
          case CONTINUE:
            parent = null;
            break;
          case SKIP_CHILDREN:
            parent = path;
            break;
          case SKIP_SIBLINGS:
            parent = path.getParentPath();
            if (parent == null) return null;
            break;
        }
      }
    }
    return null;
  }

  /**
   * Processes visible nodes in the specified tree.
   *
   * @param tree     a tree, which nodes should be processed
   * @param mapper   a function to convert a visible tree path to a corresponding object
   * @param consumer a visible path processor
   */
  public static <T> void visitVisibleRows(@NotNull JTree tree, @NotNull Function<? super TreePath, ? extends T> mapper, @NotNull Consumer<? super T> consumer) {
    visitVisibleRows(tree, path -> {
      T object = mapper.apply(path);
      if (object != null) consumer.accept(object);
      return TreeVisitor.Action.CONTINUE;
    });
  }

  /**
   * @param tree   a tree, which visible paths are processed
   * @param filter a predicate to filter visible tree paths
   * @param mapper a function to convert a visible tree path to a corresponding object
   * @return a list of objects which correspond to filtered visible paths
   */
  @NotNull
  private static <T> List<T> collectVisibleRows(@NotNull JTree tree,
                                                @NotNull Predicate<? super TreePath> filter,
                                                @NotNull Function<? super TreePath, ? extends T> mapper) {
    int count = tree.getRowCount();
    if (count == 0) return Collections.emptyList();
    List<T> list = new ArrayList<>(count);
    visitVisibleRows(tree, path -> filter.test(path) ? mapper.apply(path) : null, list::add);
    return list;
  }
}
