// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.tree;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tree.DelegatingEdtBgtTreeVisitor;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.CachingTreePath;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Range;
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
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
import static com.intellij.util.ReflectionUtil.getField;
import static java.util.stream.Collectors.toList;

public final class TreeUtil {
  public static final TreePath[] EMPTY_TREE_PATH = new TreePath[0];
  @ApiStatus.Internal
  public static final Key<Boolean> TREE_IS_BUSY = Key.create("Tree is busy doing an async operation");
  private static final Logger LOG = Logger.getInstance(TreeUtil.class);
  private static final String TREE_UTIL_SCROLL_TIME_STAMP = "TreeUtil.scrollTimeStamp";
  private static final JBIterable<Integer> NUMBERS = JBIterable.generate(0, i -> i + 1);
  private static final Key<Function<TreePath, Navigatable>> NAVIGATABLE_PROVIDER = Key.create("TreeUtil: convert TreePath to Navigatable");

  private TreeUtil() {}

  /**
   * @return a navigatable object that corresponds to the specified path,  or {@code null} otherwise
   */
  public static @Nullable Navigatable getNavigatable(@NotNull JTree tree, @Nullable TreePath path) {
    Function<? super TreePath, ? extends Navigatable> supplier = ClientProperty.get(tree, NAVIGATABLE_PROVIDER);
    return supplier == null ? getLastUserObject(Navigatable.class, path) : supplier.apply(path);
  }

  /**
   * Sets the mapping function that provides a navigatable object for a tree path.
   */
  public static void setNavigatableProvider(@NotNull JTree tree, @NotNull Function<? super TreePath, ? extends Navigatable> provider) {
    tree.putClientProperty(NAVIGATABLE_PROVIDER, provider);
  }

  public static @NotNull JBTreeTraverser<Object> treeTraverser(@NotNull JTree tree) {
    TreeModel model = tree.getModel();
    Object root = model.getRoot();
    return JBTreeTraverser.from(node -> nodeChildren(node, model)).withRoot(root);
  }

  public static @NotNull JBTreeTraverser<TreePath> treePathTraverser(@NotNull JTree tree) {
    TreeModel model = tree.getModel();
    Object root = model.getRoot();
    TreePath rootPath = root == null ? null : new CachingTreePath(root);
    return JBTreeTraverser.<TreePath>from(path -> nodeChildren(path.getLastPathComponent(), model)
      .map(o -> path.pathByAddingChild(o)))
      .withRoot(rootPath);
  }

  public static @NotNull JBIterable<Object> nodeChildren(@Nullable Object node, @NotNull TreeModel model) {
    int count = model.getChildCount(node);
    return count == 0 ? JBIterable.empty() : NUMBERS.take(count).map(index -> model.getChild(node, index));
  }

  public static @NotNull JBTreeTraverser<TreeNode> treeNodeTraverser(@Nullable TreeNode treeNode) {
    return JBTreeTraverser.<TreeNode>from(node -> nodeChildren(node)).withRoot(treeNode);
  }

  public static @NotNull JBIterable<TreeNode> nodeChildren(@Nullable TreeNode treeNode) {
    int count = treeNode == null ? 0 : treeNode.getChildCount();
    return count == 0 ? JBIterable.empty() : NUMBERS.take(count).map(index -> treeNode.getChildAt(index));
  }

  public static boolean hasManyNodes(@NotNull Tree tree, int threshold) {
    return hasManyNodes(treeTraverser(tree), threshold);
  }

  public static boolean hasManyNodes(@NotNull TreeNode node, int threshold) {
    return hasManyNodes(treeNodeTraverser(node), threshold);
  }

  private static <T> boolean hasManyNodes(@NotNull JBTreeTraverser<T> traverser, int threshold) {
    return traverser.traverse().take(threshold).size() >= threshold;
  }

  /**
   * @param tree a tree, which nodes should be found
   * @param x    a number of pixels from the left edge of the given tree
   * @param y    a number of pixels from the top of the specified tree
   * @return found visible tree path or {@code null}
   */
  public static @Nullable TreePath getPathForLocation(@NotNull JTree tree, int x, int y) {
    TreePath path = tree.getClosestPathForLocation(x, y);
    Rectangle bounds = tree.getPathBounds(path);
    return bounds != null && bounds.y <= y && y < bounds.y + bounds.height ? path : null;
  }

  /**
   * @param tree a tree, which nodes should be found
   * @param x    a number of pixels from the left edge of the given tree
   * @param y    a number of pixels from the top of the specified tree
   * @return found row number or {@code -1}
   */
  public static int getRowForLocation(@NotNull JTree tree, int x, int y) {
    return Math.max(-1, tree.getRowForPath(getPathForLocation(tree, x, y)));
  }

  /**
   * @param tree a tree to repaint
   * @param path a visible tree path to repaint
   */
  public static void repaintPath(@NotNull JTree tree, @Nullable TreePath path) {
    assert EventQueue.isDispatchThread();
    repaintBounds(tree, tree.getPathBounds(path));
  }

  /**
   * @param tree a tree to repaint
   * @param row  a row number to repaint
   */
  public static void repaintRow(@NotNull JTree tree, int row) {
    assert EventQueue.isDispatchThread();
    repaintBounds(tree, tree.getRowBounds(row));
  }

  private static void repaintBounds(@NotNull JTree tree, @Nullable Rectangle bounds) {
    // repaint extra below and above to avoid artifacts when using fractional scaling on Windows
    if (bounds != null) tree.repaint(0, bounds.y - 1, tree.getWidth(), bounds.height + 2);
  }

  /**
   * @param tree a tree, which viewable paths are processed
   * @return a list of expanded paths
   */
  public static @NotNull List<TreePath> collectExpandedPaths(@NotNull JTree tree) {
    return collectExpandedObjects(tree, Function.identity());
  }

  /**
   * @param tree a tree, which viewable paths are processed
   * @return a list of user objects which correspond to expanded paths under the specified root node
   */
  public static @NotNull List<Object> collectExpandedUserObjects(@NotNull JTree tree) {
    return collectExpandedObjects(tree, TreeUtil::getLastUserObject);
  }

  /**
   * @param tree   a tree, which viewable paths are processed
   * @param mapper a function to convert an expanded tree path to a corresponding object
   * @return a list of objects which correspond to expanded paths under the specified root node
   */
  public static @NotNull <T> List<T> collectExpandedObjects(@NotNull JTree tree, @NotNull Function<? super TreePath, ? extends T> mapper) {
    int count = tree.getRowCount();
    if (count == 0) return Collections.emptyList(); // tree is empty
    List<T> list = new ArrayList<>();
    for (int row = 0; row < count; row++) {
      if (tree.isExpanded(row)) {
        TreePath path = getVisiblePathWithValidation(tree, row, count);
        ContainerUtil.addIfNotNull(list, mapper.apply(path));
      }
    }
    return list;
  }

  public static @Nullable <T> T findObjectInPath(@Nullable TreePath path, @NotNull Class<T> clazz) {
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
  public static @NotNull <T> List<T> collectSelectedObjectsOfType(@NotNull JTree tree, @NotNull Class<? extends T> type) {
    return collectSelectedObjects(tree, path -> getLastUserObject(type, path));
  }

  /**
   * @param tree a tree, which viewable paths are processed
   * @param root an ascendant tree path to filter expanded tree paths
   * @return a list of expanded paths under the specified root node
   */
  public static @NotNull List<TreePath> collectExpandedPaths(@NotNull JTree tree, @NotNull TreePath root) {
    return collectExpandedObjects(tree, root, Function.identity());
  }

  /**
   * @param tree a tree, which viewable paths are processed
   * @param root an ascendant tree path to filter expanded tree paths
   * @return a list of user objects which correspond to expanded paths under the specified root node
   */
  public static @NotNull List<Object> collectExpandedUserObjects(@NotNull JTree tree, @NotNull TreePath root) {
    return collectExpandedObjects(tree, root, TreeUtil::getLastUserObject);
  }

  /**
   * @param tree   a tree, which viewable paths are processed
   * @param root   an ascendant tree path to filter expanded tree paths
   * @param mapper a function to convert an expanded tree path to a corresponding object
   * @return a list of objects which correspond to expanded paths under the specified root node
   */
  public static @NotNull <T> List<T> collectExpandedObjects(@NotNull JTree tree, @NotNull TreePath root, @NotNull Function<? super TreePath, ? extends T> mapper) {
    int count = tree.getRowCount();
    if (count == 0) return Collections.emptyList(); // tree is empty
    int row = tree.getRowForPath(root);
    if (row < 0) {
      return !tree.isRootVisible() && root.equals(getVisiblePathWithValidation(tree, 0, count).getParentPath())
             ? collectExpandedObjects(tree, mapper) // collect expanded objects under a hidden root
             : Collections.emptyList(); // root path is not visible
    }
    if (!tree.isExpanded(row)) return Collections.emptyList(); // root path is not expanded
    List<T> list = new ArrayList<>(count);
    ContainerUtil.addIfNotNull(list, mapper.apply(root));
    int depth = root.getPathCount();
    for (row++; row < count; row++) {
      TreePath path = getVisiblePathWithValidation(tree, row, count);
      if (depth >= path.getPathCount()) break; // not a descendant of a root path
      if (tree.isExpanded(row)) ContainerUtil.addIfNotNull(list, mapper.apply(path));
    }
    return list;
  }

  /**
   * Expands specified paths.
   * @param tree JTree to apply expansion status to
   * @param paths to expand. See {@link #collectExpandedPaths(JTree, TreePath)}
   */
  public static void restoreExpandedPaths(final @NotNull JTree tree, final @NotNull List<? extends TreePath> paths){
    if (isBulkExpandCollapseSupported(tree)) {
      //noinspection unchecked
      ((Tree)tree).expandPaths((Iterable<TreePath>)paths);
    }
    else {
      for (int i = paths.size() - 1; i >= 0; i--) {
        tree.expandPath(paths.get(i));
      }
    }
  }

  public static @NotNull TreePath getPath(@NotNull TreeNode aRootNode, @NotNull TreeNode aNode) {
    TreeNode[] nodes = getPathFromRootTo(aRootNode, aNode, true);
    return new CachingTreePath(nodes);
  }

  public static boolean isAncestor(@NotNull TreeNode ancestor, @NotNull TreeNode node) {
    TreeNode parent = node;
    while (parent != null) {
      if (parent == ancestor) return true;
      parent = parent.getParent();
    }
    return false;
  }

  public static @NotNull TreePath getPathFromRoot(@NotNull TreeNode node) {
    TreeNode[] path = getPathFromRootTo(null, node, false);
    return new CachingTreePath(path);
  }

  private static TreeNode @NotNull [] getPathFromRootTo(@Nullable TreeNode root, @NotNull TreeNode node, boolean includeRoot) {
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

  public static @Nullable TreeNode findNodeWithObject(final Object object, final @NotNull TreeModel model, final Object parent) {
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
  public static void removeSelected(final @NotNull JTree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) {
      return;
    }
    for (TreePath path : paths) {
      removeLastPathComponent((DefaultTreeModel) tree.getModel(), path).restoreSelection(tree);
    }
  }

  public static void removeLastPathComponent(final @NotNull JTree tree, final @NotNull TreePath pathToBeRemoved){
    removeLastPathComponent((DefaultTreeModel)tree.getModel(), pathToBeRemoved).restoreSelection(tree);
  }

  public static @Nullable DefaultMutableTreeNode findNodeWithObject(final @NotNull DefaultMutableTreeNode aRoot, final Object aObject) {
    return findNode(aRoot, node -> Comparing.equal(node.getUserObject(), aObject));
  }

  public static @Nullable DefaultMutableTreeNode findNode(final @NotNull DefaultMutableTreeNode aRoot,
                                                          final @NotNull Condition<? super DefaultMutableTreeNode> condition) {
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

  /**
   * Tries to select the first node in the specified tree as soon as possible.
   *
   * @param tree a tree, which node should be selected
   * @return a callback that will be done when first visible node is selected
   * @see #promiseSelectFirst
   */
  public static @NotNull ActionCallback selectFirstNode(@NotNull JTree tree) {
    return Promises.toActionCallback(promiseSelectFirst(tree));
  }

  public static @NotNull TreePath getFirstNodePath(@NotNull JTree tree) {
    TreeModel model = tree.getModel();
    Object root = model.getRoot();
    TreePath selectionPath = new CachingTreePath(root);
    if (!tree.isRootVisible() && model.getChildCount(root) > 0) {
      selectionPath = selectionPath.pathByAddingChild(model.getChild(root, 0));
    }
    return selectionPath;
  }

  public static int getRowForNode(@NotNull JTree tree, @NotNull DefaultMutableTreeNode targetNode) {
    TreeNode[] path = targetNode.getPath();
    return tree.getRowForPath(new CachingTreePath(path));
  }

  /**
   * @deprecated use {@link #promiseSelectFirstLeaf}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull TreePath getFirstLeafNodePath(@NotNull JTree tree) {
    final TreeModel model = tree.getModel();
    Object root = model.getRoot();
    TreePath selectionPath = new CachingTreePath(root);
    while (model.getChildCount(root) > 0) {
      final Object child = model.getChild(root, 0);
      selectionPath = selectionPath.pathByAddingChild(child);
      root = child;
    }
    return selectionPath;
  }

  private static @NotNull IndexTreePathState removeLastPathComponent(final @NotNull DefaultTreeModel model, final @NotNull TreePath pathToBeRemoved) {
    final IndexTreePathState selectionState = new IndexTreePathState(pathToBeRemoved);
    if (((MutableTreeNode) pathToBeRemoved.getLastPathComponent()).getParent() == null) return selectionState;
    model.removeNodeFromParent((MutableTreeNode)pathToBeRemoved.getLastPathComponent());
    return selectionState;
  }


  public static void sort(final @NotNull DefaultTreeModel model, @Nullable Comparator comparator) {
    sort((DefaultMutableTreeNode) model.getRoot(), comparator);
  }

  public static void sort(final @NotNull DefaultMutableTreeNode node, @Nullable Comparator comparator) {
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
    children.sort(comparator);
    for (int i = node.getChildCount() - 1; i >= 0; i--) {
      node.remove(i);
    }
    addChildrenTo(node, children);
  }

  public static void addChildrenTo(final @NotNull MutableTreeNode node, final @NotNull List<? extends TreeNode> children) {
    for (final Object aChildren : children) {
      final MutableTreeNode child = (MutableTreeNode)aChildren;
      node.insert(child, node.getChildCount());
    }
  }

  /** @deprecated use TreeUtil#treeTraverser() or TreeUtil#treeNodeTraverser() directly */
  @Deprecated(forRemoval = true)
  public static boolean traverse(@NotNull TreeNode node, @NotNull Traverse traverse) {
    return treeNodeTraverser(node).traverse(TreeTraversal.POST_ORDER_DFS).processEach(traverse::accept);
  }

  /** @deprecated use TreeUtil#treeTraverser() or TreeUtil#treeNodeTraverser() directly */
  @Deprecated(forRemoval = true)
  public static boolean traverseDepth(@NotNull TreeNode node, @NotNull Traverse traverse) {
    return treeNodeTraverser(node).traverse(TreeTraversal.PRE_ORDER_DFS).processEach(traverse::accept);
  }

  /**
   * Makes visible the specified tree row and selects it.
   * It does not clear selection if there is nothing to select.
   *
   * @param tree  a tree to select in
   * @param index an index of a viewable node in the given tree
   * @see JTree#getRowCount
   * @see JTree#clearSelection
   */
  public static void selectRow(@NotNull JTree tree, int index) {
    TreePath path = tree.getPathForRow(index);
    if (path != null) internalSelect(tree, path);
  }

  /**
   * Makes visible specified tree paths and select them.
   * It does not clear selection if there are no paths to select.
   *
   * @param tree  a tree to select in
   * @param paths a collection of paths to select
   * @see JTree#clearSelection
   */
  @ApiStatus.Internal
  public static void selectPaths(@NotNull JTree tree, @NotNull Collection<? extends TreePath> paths) {
    if (!paths.isEmpty()) selectPaths(tree, paths.toArray(EMPTY_TREE_PATH));
  }

  /**
   * Makes visible specified tree paths and select them.
   * It does not clear selection if there are no paths to select.
   *
   * @param tree  a tree to select in
   * @param paths an array of paths to select
   * @see JTree#clearSelection
   */
  @ApiStatus.Internal
  public static void selectPaths(@NotNull JTree tree, @NotNull TreePath @NotNull ... paths) {
    if (paths.length == 0) return;
    for (TreePath path : paths) tree.makeVisible(path);
    internalSelect(tree, paths);
  }

  public static @NotNull ActionCallback selectPath(final @NotNull JTree tree, final TreePath path) {
    return selectPath(tree, path, true);
  }

  public static @NotNull ActionCallback selectPath(final @NotNull JTree tree, final TreePath path, boolean center) {
    tree.makeVisible(path);
    Rectangle bounds = tree.getPathBounds(path);
    if (bounds == null) return ActionCallback.REJECTED;
    if (center) {
      Rectangle visible = tree.getVisibleRect();
      if (visible.y < bounds.y + bounds.height && bounds.y < visible.y + visible.height) {
        center = false; // disable centering if the given path is already visible
      }
    }
    if (center) {
      return showRowCentred(tree, tree.getRowForPath(path));
    } else {
      final int row = tree.getRowForPath(path);
      return showAndSelect(tree, row - ScrollingUtil.ROW_PADDING, row + ScrollingUtil.ROW_PADDING, row, -1);
    }
  }

  public static @NotNull ActionCallback moveDown(final @NotNull JTree tree) {
    final int size = tree.getRowCount();
    int row = tree.getLeadSelectionRow();
    if (row < size - 1) {
      row++;
      return showAndSelect(tree, row, row + 2, row, getSelectedRow(tree), false, true, true);
    } else {
      return ActionCallback.DONE;
    }
  }

  public static @NotNull ActionCallback moveUp(final @NotNull JTree tree) {
    int row = tree.getLeadSelectionRow();
    if (row > 0) {
      row--;
      return showAndSelect(tree, row - 2, row, row, getSelectedRow(tree), false, true, true);
    } else {
      return ActionCallback.DONE;
    }
  }

  public static @NotNull ActionCallback movePageUp(final @NotNull JTree tree) {
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

  public static @NotNull ActionCallback movePageDown(final @NotNull JTree tree) {
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

  private static @NotNull ActionCallback moveHome(final @NotNull JTree tree) {
    return showRowCentred(tree, 0);
  }

  private static @NotNull ActionCallback moveEnd(final @NotNull JTree tree) {
    return showRowCentred(tree, tree.getRowCount() - 1);
  }

  private static @NotNull ActionCallback showRowCentred(final @NotNull JTree tree, final int row) {
    return showRowCentered(tree, row, true);
  }

  public static @NotNull ActionCallback showRowCentered(final @NotNull JTree tree, final int row, final boolean centerHorizontally) {
    return showRowCentered(tree, row, centerHorizontally, true);
  }

  public static @NotNull ActionCallback showRowCentered(final @NotNull JTree tree, final int row, final boolean centerHorizontally, boolean scroll) {
    final int visible = getVisibleRowCount(tree);

    final int top = visible > 0 ? row - (visible - 1)/ 2 : row;
    final int bottom = visible > 0 ? top + visible - 1 : row;
    return showAndSelect(tree, top, bottom, row, -1, false, scroll, false);
  }

  public static @NotNull ActionCallback showAndSelect(final @NotNull JTree tree, int top, int bottom, final int row, final int previous) {
    return showAndSelect(tree, top, bottom, row, previous, false);
  }

  public static @NotNull ActionCallback showAndSelect(final @NotNull JTree tree, int top, int bottom, final int row, final int previous, boolean addToSelection) {
    return showAndSelect(tree, top, bottom, row, previous, addToSelection, true, false);
  }

  public static @NotNull ActionCallback showAndSelect(final @NotNull JTree tree, int top, int bottom, final int row, final int previous, final boolean addToSelection, final boolean scroll) {
    return showAndSelect(tree, top, bottom, row, previous, addToSelection, scroll, false);
  }

  public static @NotNull ActionCallback showAndSelect(final @NotNull JTree tree, int top, int bottom, final int row, final int previous, final boolean addToSelection, final boolean scroll, final boolean resetSelection) {
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

    if (comp instanceof SimpleColoredComponent renderer) {
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

    if (LOG.isTraceEnabled()) LOG.debug("tree scroll: ", path);
    tree.scrollRectToVisible(bounds);
    // try to scroll later when the tree is ready
    Object property = tree.getClientProperty(TREE_UTIL_SCROLL_TIME_STAMP);
    long stamp = property instanceof Long ? (Long)property + 1L : Long.MIN_VALUE;
    tree.putClientProperty(TREE_UTIL_SCROLL_TIME_STAMP, stamp);
    ClientProperty.put(tree, TREE_IS_BUSY, true);
    // store relative offset because the row can be moved during the tree updating
    int offset = rowBounds.y - bounds.y;

    scrollToVisible(tree, path, bounds, offset, stamp, callback::setDone, 3);

    return callback;
  }

  private static void scrollToVisible(JTree tree, TreePath path, Rectangle bounds, int offset, long expected, Runnable done, int attempt) {
    Runnable scroll = () -> {
      Rectangle pathBounds = attempt <= 0 ? null : tree.getPathBounds(path);
      if (pathBounds != null) {
        Object property = tree.getClientProperty(TREE_UTIL_SCROLL_TIME_STAMP);
        long stamp = property instanceof Long ? (Long)property : Long.MAX_VALUE;
        if (LOG.isTraceEnabled()) LOG.debug("tree scroll ", attempt, stamp == expected ? ": try again: " : ": ignore: ", path);
        if (stamp == expected) {
          bounds.y = pathBounds.y - offset; // restore bounds according to the current row
          Rectangle visible = tree.getVisibleRect();
          if (bounds.y < visible.y || bounds.y > visible.y + Math.max(0, visible.height - bounds.height)) {
            tree.scrollRectToVisible(bounds);
            scrollToVisible(tree, path, bounds, offset, expected, done, attempt - 1);
            return; // try to scroll again
          }
        }
      }
      ClientProperty.remove(tree, TREE_IS_BUSY);
      done.run();
    };
    SwingUtilities.invokeLater(scroll);
  }

  // this method returns FIRST selected row but not LEAD
  private static int getSelectedRow(final @NotNull JTree tree) {
    return tree.getRowForPath(tree.getSelectionPath());
  }

  private static int getFirstVisibleRow(final @NotNull JTree tree) {
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

  /**
   * Returns a number of tree rows currently visible. Do not mix with {@link JTree#getVisibleRowCount()}
   * which returns a preferred number of rows to be displayed within a scroll pane.
   *
   * @param tree tree to get the number of visible rows
   * @return number of visible rows, including partially visible ones. Not more than total number of tree rows.
   */
  public static int getVisibleRowCount(final @NotNull JTree tree) {
    final Rectangle visible = tree.getVisibleRect();
    if (visible == null) return 0;

    int rowCount = tree.getRowCount();
    if (rowCount <= 0) return 0;

    int firstRow;
    int lastRow;
    int rowHeight = tree.getRowHeight();
    if (rowHeight > 0) {
      Insets insets = tree.getInsets();
      int top = visible.y - insets.top;
      int bottom = visible.y + visible.height - insets.top;
      firstRow = Math.max(0, Math.min(top / rowHeight, rowCount - 1));
      lastRow = Math.max(0, Math.min(bottom / rowHeight, rowCount - 1));
    } else {
      firstRow = tree.getClosestRowForLocation(visible.x, visible.y);
      lastRow = tree.getClosestRowForLocation(visible.x, visible.y + visible.height);
    }
    return lastRow - firstRow + 1;
  }

  public static void installActions(final @NotNull JTree tree) {
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

  private static void copyAction(final @NotNull JTree tree, String original, String copyTo) {
    final Action action = tree.getActionMap().get(original);
    if (action != null) {
      tree.getActionMap().put(copyTo, action);
    }
  }

  /**
   * @param tree               a tree, which nodes should be collapsed
   * @param keepSelectionLevel a minimal path count of a lead selection path or {@code -1} to restore old selection
   */
  public static void collapseAll(@NotNull JTree tree, final int keepSelectionLevel) {
    collapseAll(tree, false, keepSelectionLevel);
  }

  /**
   * @param tree               a tree, which nodes should be collapsed
   * @param strict             use {@code false} if a single top level node should not be collapsed
   * @param keepSelectionLevel a minimal path count of a lead selection path or {@code -1} to restore old selection
   */
  public static void collapseAll(@NotNull JTree tree, boolean strict, int keepSelectionLevel) {
    assert EventQueue.isDispatchThread();
    int row = tree.getRowCount();
    if (row <= 1) return; // nothing to collapse

    final TreePath leadSelectionPath = tree.getLeadSelectionPath();

    int minCount = 1; // allowed path count to collapse
    if (!tree.isRootVisible()) minCount++;
    if (!tree.getShowsRootHandles()) {
      minCount++;
      strict = true;
    }

    // use the parent path of the normalized selection path to prohibit its collapsing
    TreePath prohibited = leadSelectionPath == null ? null : normalize(leadSelectionPath, minCount, keepSelectionLevel).getParentPath();
    // Collapse all
    var paths = new ArrayList<TreePath>();
    while (0 < row--) {
      if (!strict && row == 0) break;
      TreePath path = tree.getPathForRow(row);
      assert path != null : "path is not found at row " + row;
      int pathCount = path.getPathCount();
      if (pathCount < minCount) continue;
      if (pathCount == minCount && row > 0) strict = true;
      if (!isAlwaysExpand(path) && !path.isDescendant(prohibited)) {
        paths.add(path);
      }
    }
    collapsePaths(tree, paths);
    if (leadSelectionPath == null) return; // no selection to restore
    if (!strict) minCount++; // top level node is not collapsed
    internalSelect(tree, normalize(leadSelectionPath, minCount, keepSelectionLevel));
  }

  public static boolean isBulkExpandCollapseSupported(@NotNull JTree tree) {
    return Tree.isBulkExpandCollapseSupported() && tree instanceof Tree;
  }

  public static void expandPaths(@NotNull JTree tree, @Nullable Iterable<TreePath> paths) {
    if (paths == null) {
      return;
    }
    if (Tree.isBulkExpandCollapseSupported() && tree instanceof Tree jbTree) {
      jbTree.expandPaths(paths);
    }
    else {
      paths.forEach(tree::expandPath);
    }
  }

  public static void collapsePaths(@NotNull JTree tree, @Nullable Iterable<TreePath> paths) {
    if (paths == null) {
      return;
    }
    if (Tree.isBulkExpandCollapseSupported() && tree instanceof Tree jbTree) {
      jbTree.collapsePaths(paths);
    }
    else {
      paths.forEach(tree::collapsePath);
    }
  }

  /**
   * @param path               a path to normalize
   * @param minCount           a minimal number of elements in the resulting path
   * @param keepSelectionLevel a maximal number of elements in the selection path or negative value to preserve the given path
   * @return a parent path with the specified number of elements, or the given {@code path} if it does not have enough elements
   */
  private static @NotNull TreePath normalize(@NotNull TreePath path, int minCount, int keepSelectionLevel) {
    if (keepSelectionLevel < 0) return path;
    if (keepSelectionLevel > minCount) minCount = keepSelectionLevel;
    int pathCount = path.getPathCount();
    while (minCount < pathCount--) path = path.getParentPath();
    assert path != null : "unexpected minCount: " + minCount;
    return path;
  }

  /**
   * @param path a path to expand (or to collapse)
   * @return {@code true} if node should be expanded (or should not be collapsed) automatically
   * @see AbstractTreeNode#isAlwaysExpand
   */
  private static boolean isAlwaysExpand(@NotNull TreePath path) {
    AbstractTreeNode<?> node = getAbstractTreeNode(path);
    return node != null && node.isAlwaysExpand();
  }

  public static void selectNode(final @NotNull JTree tree, final TreeNode node) {
    selectPath(tree, getPathFromRoot(node));
  }

  public static void moveSelectedRow(@NotNull JTree tree, int direction) {
    TreePath selectionPath = tree.getSelectionPath();
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode)treeNode.getParent();
    int idx = parent.getIndex(treeNode);
    if (idx + direction < 0 || idx + direction >= parent.getChildCount()) {
      return;
    }
    DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
    model.removeNodeFromParent(treeNode);
    model.insertNodeInto(treeNode, parent, idx + direction);
    selectNode(tree, treeNode);
  }

  public static @NotNull List<TreeNode> listChildren(final @NotNull TreeNode node) {
    //ThreadingAssertions.assertEventDispatchThread();
    int size = node.getChildCount();
    ArrayList<TreeNode> result = new ArrayList<>(size);
    for(int i = 0; i < size; i++){
      TreeNode child = node.getChildAt(i);
      LOG.assertTrue(child != null);
      result.add(child);
    }
    return result;
  }

  public static void expandRootChildIfOnlyOne(final @Nullable JTree tree) {
    if (tree == null) return;
    Runnable runnable = () -> {
      TreeModel model = tree.getModel();
      Object root = model.getRoot();
      if (root == null) return;
      TreePath rootPath = new CachingTreePath(root);
      tree.expandPath(rootPath);
      if (model.getChildCount(root) == 1) {
        Object firstChild = model.getChild(root, 0);
        tree.expandPath(rootPath.pathByAddingChild(firstChild));
      }
    };
    EdtInvocationManager.invokeLaterIfNeeded(runnable);
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
    promiseExpandAll(tree).onSuccess(result -> EdtInvocationManager.invokeLaterIfNeeded(onDone));
  }

  /**
   * Promises to expand all nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree a tree, which nodes should be expanded
   * @return a promise that will be succeeded when all nodes are expanded
   */
  public static @NotNull Promise<?> promiseExpandAll(@NotNull JTree tree) {
    return promiseExpand(tree, Integer.MAX_VALUE);
  }

  /**
   * Expands n levels of the tree counting from the root
   * @param tree to expand nodes of
   * @param levels depths of the expansion
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
    promiseExpand(tree, depth).onSuccess(result -> EdtInvocationManager.invokeLaterIfNeeded(onDone));
  }

  /**
   * Promises to expand some nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree  a tree, which nodes should be expanded
   * @param depth a depth starting from the root node
   * @return a promise that will be succeeded when all needed nodes are expanded
   */
  public static @NotNull Promise<?> promiseExpand(@NotNull JTree tree, int depth) {
    return promiseExpand(tree, depth, path -> depth < Integer.MAX_VALUE || isIncludedInExpandAll(path));
  }

  @ApiStatus.Internal
  public static @NotNull Promise<?> promiseExpand(@NotNull JTree tree, int depth, @NotNull Predicate<@NotNull TreePath> predicate) {
    AsyncPromise<?> promise = new AsyncPromise<>();
    promiseMakeVisible(tree, new TreeVisitor() {
      @Override
      public @NotNull TreeVisitor.VisitThread visitThread() {
        return Registry.is("ide.tree.background.expand", true) ? VisitThread.BGT : VisitThread.EDT;
      }

      @Override
      public @NotNull Action visit(@NotNull TreePath path) {
        return depth < path.getPathCount()
               ? TreeVisitor.Action.SKIP_SIBLINGS
               : predicate.test(path)
               ? TreeVisitor.Action.CONTINUE
               : TreeVisitor.Action.SKIP_CHILDREN;
      }
    }, promise)
      .onError(promise::setError)
      .onSuccess(path -> {
        if (promise.isCancelled()) return;
        promise.setResult(null);
      });
    return promise;
  }

  private static boolean isIncludedInExpandAll(@NotNull TreePath path) {
    var value = getLastUserObject(path);
    if (value instanceof AbstractTreeNode<?> node) {
      return node.isIncludedInExpandAll();
    }
    else {
      return true;
    }
  }

  public static @NotNull ActionCallback selectInTree(DefaultMutableTreeNode node, boolean requestFocus, @NotNull JTree tree) {
    return selectInTree(node, requestFocus, tree, true);
  }

  public static @NotNull ActionCallback selectInTree(@Nullable DefaultMutableTreeNode node, boolean requestFocus, @NotNull JTree tree, boolean center) {
    if (node == null) return ActionCallback.DONE;

    final TreePath treePath = new CachingTreePath(node.getPath());
    tree.expandPath(treePath);
    if (requestFocus) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(tree, true));
    }
    return selectPath(tree, treePath, center);
  }

  public static @NotNull ActionCallback selectInTree(Project project, @Nullable DefaultMutableTreeNode node, boolean requestFocus, @NotNull JTree tree, boolean center) {
    if (node == null) return ActionCallback.DONE;

    final TreePath treePath = new CachingTreePath(node.getPath());
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
  public static @NotNull List<TreePath> collectSelectedPaths(@NotNull JTree tree) {
    return collectSelectedObjects(tree, Function.identity());
  }

  /**
   * @param tree a tree, which selection is processed
   * @return a list of user objects which correspond to all selected paths
   */
  public static @NotNull List<Object> collectSelectedUserObjects(@NotNull JTree tree) {
    return collectSelectedObjects(tree, TreeUtil::getLastUserObject);
  }

  /**
   * @param tree   a tree, which selection is processed
   * @param mapper a function to convert a selected tree path to a corresponding object
   * @return a list of objects which correspond to all selected paths
   */
  public static @NotNull <T> List<T> collectSelectedObjects(@NotNull JTree tree, @NotNull Function<? super TreePath, ? extends T> mapper) {
    return getSelection(tree, path -> isViewable(tree, path), mapper);
  }

  /**
   * @param tree a tree, which selection is processed
   * @param root an ascendant tree path to filter selected tree paths
   * @return a list of selected paths under the specified root node
   */
  public static @NotNull List<TreePath> collectSelectedPaths(@NotNull JTree tree, @NotNull TreePath root) {
    return collectSelectedObjects(tree, root, Function.identity());
  }

  /**
   * @param tree a tree, which selection is processed
   * @param root an ascendant tree path to filter selected tree paths
   * @return a list of user objects which correspond to selected paths under the specified root node
   */
  public static @NotNull List<Object> collectSelectedUserObjects(@NotNull JTree tree, @NotNull TreePath root) {
    return collectSelectedObjects(tree, root, TreeUtil::getLastUserObject);
  }

  /**
   * @param tree   a tree, which selection is processed
   * @param root   an ascendant tree path to filter selected tree paths
   * @param mapper a function to convert a selected tree path to a corresponding object
   * @return a list of objects which correspond to selected paths under the specified root node
   */
  public static @NotNull <T> List<T> collectSelectedObjects(@NotNull JTree tree, @NotNull TreePath root, @NotNull Function<? super TreePath, ? extends T> mapper) {
    if (!tree.isVisible(root)) return Collections.emptyList(); // invisible path should not be selected
    return getSelection(tree, path -> isViewable(tree, path) && root.isDescendant(path), mapper);
  }

  private static @NotNull <T> List<T> getSelection(@NotNull JTree tree, @NotNull Predicate<? super TreePath> filter, @NotNull Function<? super TreePath, ? extends T> mapper) {
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

  public static @Nullable Range<Integer> getExpandControlRange(final @NotNull JTree aTree, final @Nullable TreePath path) {
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

  @ApiStatus.Experimental
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

  @ApiStatus.Experimental
  public static boolean isLocationInExpandControl(@NotNull JTree tree, int x, int y) {
    if (LazyLocationInExpandControl.METHOD == null) return false; // system error
    return isLocationInExpandControl(tree, tree.getClosestPathForLocation(x, y), x, y);
  }

  @ApiStatus.Experimental
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

  @ApiStatus.Experimental
  public static void invalidateCacheAndRepaint(@Nullable TreeUI ui) {
    if (ui instanceof BasicTreeUI basic) {
      if (null == getField(BasicTreeUI.class, ui, JTree.class, "tree")) {
        LOG.warn(new IllegalStateException("tree is not properly initialized yet"));
        return;
      }
      EdtInvocationManager.invokeLaterIfNeeded(() -> basic.setLeftChildIndent(basic.getLeftChildIndent()));
    }
  }

  public static @NotNull RelativePoint getPointForSelection(@NotNull JTree aTree) {
    final int[] rows = aTree.getSelectionRows();
    if (rows == null || rows.length == 0) {
      return RelativePoint.getCenterOf(aTree);
    }
    return getPointForRow(aTree, rows[rows.length - 1]);
  }

  public static @NotNull RelativePoint getPointForRow(@NotNull JTree aTree, int aRow) {
    return getPointForPath(aTree, aTree.getPathForRow(aRow));
  }

  public static @NotNull RelativePoint getPointForPath(@NotNull JTree aTree, TreePath path) {
    final Rectangle rowBounds = aTree.getPathBounds(path);
    rowBounds.x += 20;
    return getPointForBounds(aTree, rowBounds);
  }

  public static @NotNull RelativePoint getPointForBounds(JComponent aComponent, final @NotNull Rectangle aBounds) {
    return new RelativePoint(aComponent, new Point(aBounds.x, (int)aBounds.getMaxY()));
  }

  public static boolean isOverSelection(final @NotNull JTree tree, final @NotNull Point point) {
    TreePath path = tree.getPathForLocation(point.x, point.y);
    return path != null && tree.getSelectionModel().isPathSelected(path);
  }

  public static void dropSelectionButUnderPoint(@NotNull JTree tree, @NotNull Point treePoint) {
    final TreePath toRetain = tree.getPathForLocation(treePoint.x, treePoint.y);
    if (toRetain == null) return;

    TreePath[] selection = tree.getSelectionModel().getSelectionPaths();
    selection = selection == null ? EMPTY_TREE_PATH : selection;
    for (TreePath each : selection) {
      if (toRetain.equals(each)) continue;
      tree.getSelectionModel().removeSelectionPath(each);
    }
  }

  public static boolean isLoadingPath(@Nullable TreePath path) {
    return path != null && isLoadingNode(path.getLastPathComponent());
  }

  public static boolean isLoadingNode(@Nullable Object node) {
    while (node != null) {
      if (node instanceof LoadingNode) return true;
      if (!(node instanceof DefaultMutableTreeNode)) return false;
      node = ((DefaultMutableTreeNode)node).getUserObject();
    }
    return false;
  }

  public static @Nullable Object getUserObject(@Nullable Object node) {
    return node instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode)node).getUserObject() : node;
  }

  public static @Nullable <T> T getUserObject(@NotNull Class<T> type, @Nullable Object node) {
    node = getUserObject(node);
    return type.isInstance(node) ? type.cast(node) : null;
  }

  /**
   * @return a user object retrieved from the last component of the specified {@code path}
   */
  public static @Nullable Object getLastUserObject(@Nullable TreePath path) {
    return path == null ? null : getUserObject(path.getLastPathComponent());
  }

  public static @Nullable <T> T getLastUserObject(@NotNull Class<T> type, @Nullable TreePath path) {
    return path == null ? null : getUserObject(type, path.getLastPathComponent());
  }

  public static @Nullable AbstractTreeNode<?> getAbstractTreeNode(@Nullable Object node) {
    return getUserObject(AbstractTreeNode.class, node);
  }

  public static @Nullable AbstractTreeNode<?> getAbstractTreeNode(@Nullable TreePath path) {
    return getLastUserObject(AbstractTreeNode.class, path);
  }

  public static <T> @Nullable T getParentNodeOfType(@Nullable AbstractTreeNode<?> node, @NotNull Class<T> aClass) {
    for (AbstractTreeNode<?> cur = node; cur != null; cur = cur.getParent()) {
      if (aClass.isInstance(cur)) return (T)cur;
    }
    return null;
  }

  public static @Nullable TreePath getSelectedPathIfOne(@Nullable JTree tree) {
    TreePath[] paths = tree == null ? null : tree.getSelectionPaths();
    return paths != null && paths.length == 1 ? paths[0] : null;
  }

  /** @deprecated use TreeUtil#treePathTraverser() */
  @Deprecated(forRemoval = true)
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

  public static @NotNull Comparator<TreePath> getDisplayOrderComparator(final @NotNull JTree tree) {
    return Comparator.comparingInt(tree::getRowForPath);
  }

  private static void expandPathWithDebug(@NotNull JTree tree, @NotNull TreePath path) {
    if (LOG.isTraceEnabled()) LOG.debug("tree expand path: ", path);
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
   * Promises to expand a node (specified by the path) in the given tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree a tree, which nodes should be expanded
   * @param path a tree path to a node that should be expanded
   * @return a promise that will be succeeded only if path is found and expanded
   */
  public static @NotNull Promise<TreePath> promiseExpand(@NotNull JTree tree, @NotNull TreePath path) {
    return promiseExpand(tree, new TreeVisitor.ByTreePath<>(path, node -> node));
  }

  /**
   * Promises to expand a node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree    a tree, which nodes should be expanded
   * @param visitor a visitor that controls expanding of tree nodes
   * @return a promise that will be succeeded only if path is found and expanded
   */
  public static @NotNull Promise<TreePath> promiseExpand(@NotNull JTree tree, @NotNull TreeVisitor visitor) {
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
   * @return a promise that will be succeeded only if paths are found and expanded
   */
  public static @NotNull Promise<List<TreePath>> promiseExpand(@NotNull JTree tree, @NotNull Stream<? extends TreeVisitor> visitors) {
    return promiseMakeVisibleAll(tree, visitors, paths -> expandPaths(tree, paths));
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
   * Promises to make visible a node (specified by the path) in the given tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree a tree, which nodes should be made visible
   * @param path a tree path to a node that should be made visible
   * @return a promise that will be succeeded only if path is found and made visible
   */
  public static @NotNull Promise<TreePath> promiseMakeVisible(@NotNull JTree tree, @NotNull TreePath path) {
    return promiseMakeVisible(tree, new TreeVisitor.ByTreePath<>(path, node -> node));
  }

  /**
   * Promises to make visible a node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree    a tree, which nodes should be made visible
   * @param visitor a visitor that controls expanding of tree nodes
   * @return a promise that will be succeeded only if path is found and made visible
   */
  public static @NotNull Promise<TreePath> promiseMakeVisible(@NotNull JTree tree, @NotNull TreeVisitor visitor) {
    return promiseMakeVisibleOne(tree, visitor, null);
  }

  private static @NotNull Promise<TreePath> promiseMakeVisibleOne(@NotNull JTree tree,
                                                                  @NotNull TreeVisitor visitor,
                                                                  @Nullable Consumer<? super TreePath> consumer) {
    AsyncPromise<TreePath> promise = new AsyncPromise<>();
    promiseMakeVisible(tree, visitor, promise)
      .onError(promise::setError)
      .onSuccess(path -> {
        if (promise.isCancelled()) {
          return;
        }
        EdtInvocationManager.invokeLaterIfNeeded(() ->
          WriteIntentReadAction.run((Runnable)() -> {
            if (promise.isCancelled()) return;
            if (tree.isVisible(path)) {
              if (consumer != null) consumer.accept(path);
              promise.setResult(path);
            }
            else {
              promise.cancel();
            }
          })
        );
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
   * @return a promise that will be succeeded only if path are found and made visible
   */
  @SuppressWarnings("unused")
  public static @NotNull Promise<List<TreePath>> promiseMakeVisible(@NotNull JTree tree, @NotNull Stream<? extends TreeVisitor> visitors) {
    return promiseMakeVisibleAll(tree, visitors, null);
  }

  private static Promise<List<TreePath>> promiseMakeVisibleAll(@NotNull JTree tree,
                                                               @NotNull Stream<? extends TreeVisitor> visitors,
                                                               @Nullable Consumer<? super List<TreePath>> consumer) {
    AsyncPromise<List<TreePath>> promise = new AsyncPromise<>();
    return promiseVisitAll(tree, visitors, promise, visitor -> promiseMakeVisible(tree, visitor, promise), consumer);
  }

  private static Promise<List<TreePath>> promiseVisitAll(@NotNull JTree tree,
                                                               @NotNull Stream<? extends TreeVisitor> visitors,
                                                               @NotNull AsyncPromise<List<TreePath>> promise,
                                                               @NotNull Function<? super TreeVisitor, Promise<TreePath>> visitAction,
                                                               @Nullable Consumer<? super List<TreePath>> consumer) {
    List<Promise<TreePath>> promises = visitors
      .filter(Objects::nonNull)
      .map(visitAction)
      .collect(toList());
    Promises.collectResults(promises, true)
      .onError(promise::setError)
      .onSuccess(paths -> {
        if (promise.isCancelled()) return;
        if (!ContainerUtil.isEmpty(paths)) {
          EdtInvocationManager.invokeLaterIfNeeded(() -> {
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

  private static @NotNull Promise<TreePath> promiseMakeVisible(@NotNull JTree tree, @NotNull TreeVisitor visitor, @NotNull AsyncPromise<?> promise) {
    MakeVisibleVisitor makeVisibleVisitor =
      !(tree.getModel() instanceof TreeVisitor.Acceptor) && Tree.isBulkExpandCollapseSupported()
      ? new BulkMakeVisibleVisitor(tree, visitor, promise)
      : new BackgroundMakeVisibleVisitor(tree, visitor, promise);
    if (tree instanceof Tree jbTree) {
      jbTree.suspendExpandCollapseAccessibilityAnnouncements();
    }
    return promiseVisit(tree, makeVisibleVisitor).onProcessed(path -> {
      makeVisibleVisitor.finish();
    });
  }

  private abstract static class MakeVisibleVisitor extends DelegatingEdtBgtTreeVisitor {

    protected final JTree tree;
    protected final @NotNull AsyncPromise<?> promise;
    private final @NotNull Set<@NotNull TreePath> expandRoots = new LinkedHashSet<>();

    private MakeVisibleVisitor(@NotNull JTree tree, @NotNull TreeVisitor delegate, @NotNull AsyncPromise<?> promise) {
      super(delegate);
      this.tree = tree;
      this.promise = promise;
    }

    @Override
    public @Nullable Action preVisitEDT(@NotNull TreePath path) {
      return promise.isCancelled() ? TreeVisitor.Action.SKIP_SIBLINGS : null;
    }

    @Override
    public @NotNull Action postVisitEDT(@NotNull TreePath path, @NotNull TreeVisitor.Action action) {
      if (action == TreeVisitor.Action.CONTINUE || action == TreeVisitor.Action.INTERRUPT) {
        if (checkCancelled(path)) {
          return TreeVisitor.Action.SKIP_SIBLINGS;
        }
        var model = tree.getModel();
        if (action == TreeVisitor.Action.CONTINUE && model != null && !model.isLeaf(path.getLastPathComponent()) && !tree.isExpanded(path)) {
          if (!isUnderExpandRoot(path)) {
            expandRoots.add(path);
          }
          doExpand(path);
        }
      }
      return action;
    }
    
    protected abstract boolean checkCancelled(@NotNull TreePath path);

    protected abstract void doExpand(@NotNull TreePath path);

    private boolean isUnderExpandRoot(@NotNull TreePath path) {
      var parent = path.getParentPath();
      while (parent != null) {
        if (expandRoots.contains(parent)) {
          return true;
        }
        parent = parent.getParentPath();
      }
      return false;
    }

    final void finish() {
      finishExpanding();
      announceExpanded();
    }

    void finishExpanding() { }

    void announceExpanded() {
      if (tree instanceof Tree jbTree) {
        jbTree.resumeExpandCollapseAccessibilityAnnouncements();
        for (TreePath expandRoot : expandRoots) {
          jbTree.fireAccessibleTreeExpanded(expandRoot);
        }
      }
    }
  }

  private static class BackgroundMakeVisibleVisitor extends MakeVisibleVisitor {

    private BackgroundMakeVisibleVisitor(@NotNull JTree tree, @NotNull TreeVisitor delegate, @NotNull AsyncPromise<?> promise) {
      super(tree, delegate, promise);
    }

    @Override
    protected boolean checkCancelled(@NotNull TreePath path) {
      if (tree.isVisible(path)) {
        return false;
      }
      else {
        if (!promise.isCancelled()) {
          if (LOG.isTraceEnabled()) LOG.debug("tree expand canceled");
          promise.cancel();
        }
        return true;
      }
    }

    @Override
    protected void doExpand(@NotNull TreePath path) {
      expandPathWithDebug(tree, path);
    }
  }

  private static class BulkMakeVisibleVisitor extends MakeVisibleVisitor {

    private final @NotNull List<@NotNull TreePath> pathsToExpand = new ArrayList<>();

    private BulkMakeVisibleVisitor(@NotNull JTree tree, @NotNull TreeVisitor delegate, @NotNull AsyncPromise<?> promise) {
      super(tree, delegate, promise);
    }

    @Override
    protected boolean checkCancelled(@NotNull TreePath path) {
      return false; // bulk expand is performed in a single non-cancelable operation
    }

    @Override
    protected void doExpand(@NotNull TreePath path) {
      pathsToExpand.add(path);
    }

    @Override
    void finishExpanding() {
      expandPaths(tree, pathsToExpand);
    }
  }

  /**
   * Promises to select a node (specified by the path) in the given tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree a tree, which nodes should be selected
   * @param path a tree path to a node that should be selected
   * @return a promise that will be succeeded only if path is found and selected
   */
  public static @NotNull Promise<TreePath> promiseSelect(@NotNull JTree tree, @NotNull TreePath path) {
    return promiseSelect(tree, new TreeVisitor.ByTreePath<>(path, node -> node));
  }

  /**
   * Promises to select a node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree    a tree, which nodes should be selected
   * @param visitor a visitor that controls expanding of tree nodes
   * @return a promise that will be succeeded only if path is found and selected
   */
  public static @NotNull Promise<TreePath> promiseSelect(@NotNull JTree tree, @NotNull TreeVisitor visitor) {
    return promiseMakeVisibleOne(tree, visitor, path -> internalSelect(tree, path));
  }

  /**
   * Promises to select several nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree     a tree, which nodes should be selected
   * @param visitors visitors to control expanding of tree nodes
   * @return a promise that will be succeeded only if paths are found and selected
   */
  public static @NotNull Promise<List<TreePath>> promiseSelect(@NotNull JTree tree, @NotNull Stream<? extends TreeVisitor> visitors) {
    return promiseMakeVisibleAll(tree, visitors, paths -> internalSelect(tree, paths.toArray(EMPTY_TREE_PATH)));
  }

  private static void internalSelect(@NotNull JTree tree, @NotNull TreePath @NotNull ... paths) {
    assert EventQueue.isDispatchThread();
    if (paths.length == 0) return;
    tree.setSelectionPaths(paths);
    for (TreePath path : paths) {
      if (scrollToVisible(tree, path, Registry.is("ide.tree.autoscrollToVCenter", false))) {
        break;
      }
    }
  }

  /**
   * @param tree     a tree to scroll
   * @param path     a visible tree path to scroll
   * @param centered {@code true} to show the specified path in the center
   * @return {@code false} if a path is hidden (under a collapsed parent)
   */
  public static boolean scrollToVisible(@NotNull JTree tree, @NotNull TreePath path, boolean centered) {
    assert EventQueue.isDispatchThread();
    Rectangle bounds = tree.getPathBounds(path);
    if (bounds == null) {
      if (LOG.isTraceEnabled()) LOG.debug("cannot scroll to: ", path);
      return false;
    }
    internalScroll(tree, bounds, centered);
    // notify screen readers that they should notify the user that the visual appearance of the component has changed
    AccessibleContext context = tree.getAccessibleContext();
    if (context != null) context.firePropertyChange(AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY, false, true);
    // try to scroll later when the tree is ready
    long stamp = 1L + getScrollTimeStamp(tree);
    tree.putClientProperty(TREE_UTIL_SCROLL_TIME_STAMP, stamp);
    ClientProperty.put(tree, TREE_IS_BUSY, true);
    EdtScheduler.getInstance().schedule(5, () -> {
      Rectangle boundsLater = stamp != getScrollTimeStamp(tree) ? null : tree.getPathBounds(path);
      if (boundsLater != null) internalScroll(tree, boundsLater, centered);
      ClientProperty.remove(tree, TREE_IS_BUSY);
    });
    return true;
  }

  private static void internalScroll(@NotNull JTree tree, @NotNull Rectangle bounds, boolean centered) {
    JViewport viewport = ComponentUtil.getViewport(tree);
    if (viewport != null) {
      int width = viewport.getWidth();
      if (!centered && tree instanceof Tree && !((Tree)tree).isHorizontalAutoScrollingEnabled()) {
        bounds.x = -tree.getX();
        bounds.width = width;
      }
      else {
        int control = JBUIScale.scale(20); // calculate a control width
        bounds.x = Math.max(0, bounds.x - control);
        bounds.width = bounds.x > 0 ? Math.min(bounds.width + control, centered ? width : width / 2) : width;
      }
      int height = viewport.getHeight();
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
    tree.scrollRectToVisible(bounds);
  }

  private static long getScrollTimeStamp(@NotNull JTree tree) {
    Object property = tree.getClientProperty(TREE_UTIL_SCROLL_TIME_STAMP);
    return property instanceof Long ? (Long)property : Long.MIN_VALUE;
  }

  /**
   * Promises to select the first node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree a tree, which node should be selected
   * @return a promise that will be succeeded when first visible node is selected
   */
  public static @NotNull Promise<TreePath> promiseSelectFirst(@NotNull JTree tree) {
    return promiseSelect(tree, path -> isHiddenRoot(tree, path)
                                       ? TreeVisitor.Action.CONTINUE
                                       : TreeVisitor.Action.INTERRUPT);
  }

  private static boolean isHiddenRoot(@NotNull JTree tree, @NotNull TreePath path) {
    return !tree.isRootVisible() && path.getParentPath() == null;
  }

  /**
   * Promises to select the first leaf node in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree a tree, which node should be selected
   * @return a promise that will be succeeded when first leaf node is made visible and selected
   */
  public static @NotNull Promise<TreePath> promiseSelectFirstLeaf(@NotNull JTree tree) {
    AtomicReference<TreePath> reference = new AtomicReference<>();
    AsyncPromise<TreePath> promise = new AsyncPromise<>();
    promiseMakeVisible(tree, path -> {
      TreePath parent = reference.getAndSet(path);
      if (getPathCount(parent) == getPathCount(path.getParentPath())) return TreeVisitor.Action.CONTINUE;
      internalSelect(tree, parent);
      promise.setResult(parent);
      return TreeVisitor.Action.INTERRUPT;
    }, promise)
      .onError(promise::setError)
      .onSuccess(path -> {
        if (!promise.isDone()) {
          TreePath tail = reference.get();
          if (tail == null || isHiddenRoot(tree, tail)) {
            promise.cancel();
          }
          else {
            internalSelect(tree, tail);
            promise.setResult(tail);
          }
        }
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
    promiseVisit(tree, visitor).onSuccess(path -> EdtInvocationManager.invokeLaterIfNeeded(() -> consumer.accept(path)));
  }

  /**
   * Promises to process nodes in the specified tree.
   * <strong>NB!:</strong>
   * The returned promise may be resolved immediately,
   * if this method is called on inappropriate background thread.
   *
   * @param tree    a tree, which nodes should be processed
   * @param visitor a visitor that controls processing of tree nodes
   * @return a promise that will be succeeded when visiting is finished
   */
  public static @NotNull Promise<TreePath> promiseVisit(@NotNull JTree tree, @NotNull TreeVisitor visitor) {
    TreeModel model = tree.getModel();
    if (model instanceof TreeVisitor.Acceptor acceptor) {
      return acceptor.accept(visitor);
    }
    if (model == null) return Promises.rejectedPromise("tree model is not set");
    AsyncPromise<TreePath> promise = new AsyncPromise<>();
    // Code run under "invokeLaterIfNeeded" must not touch PSI, but this code touches it.
    EdtInvocationManager.invokeLaterIfNeeded(() -> ReadAction.run(() -> promise.setResult(visitModel(model, visitor))));
    return promise;
  }

  @ApiStatus.Internal
  public static Promise<List<TreePath>> promiseVisit(@NotNull JTree tree,
                                                        @NotNull Stream<? extends TreeVisitor> visitors,
                                                        @Nullable Consumer<? super List<TreePath>> consumer) {
    AsyncPromise<List<TreePath>> promise = new AsyncPromise<>();
    return promiseVisitAll(tree, visitors, promise, visitor -> promiseVisit(tree, visitor), consumer);
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

    TreePath path = new CachingTreePath(root);
    switch (visitor.visit(path)) {
      case INTERRUPT -> {
        return path; // root path is found
      }
      case CONTINUE -> {
        // visit children
      }
      default -> {
        return null; // skip children
      }
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
          case INTERRUPT -> {
            return next; // path is found
          }
          case CONTINUE -> {
            path = next;
            stack.push(children(model, path));
          }
          case SKIP_SIBLINGS -> siblings.clear();
          case SKIP_CHILDREN -> {}
        }
      }
    }
    LOG.assertTrue(stack.isEmpty());
    return null;
  }

  private static @NotNull Deque<TreePath> children(@NotNull TreeModel model, @NotNull TreePath path) {
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
      TreePath path = getVisiblePathWithValidation(tree, row, count);
      if (parent == null || !parent.isDescendant(path)) {
        switch (visitor.visit(path)) {
          case INTERRUPT -> {
            return path; // path is found
          }
          case CONTINUE -> parent = null;
          case SKIP_CHILDREN -> parent = path;
          case SKIP_SIBLINGS -> {
            parent = path.getParentPath();
            if (parent == null) return null;
          }
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
   * @param tree a tree, which nodes should be iterated
   * @param lead a starting tree path
   * @return next tree path with the same parent, or {@code null} if it is not found
   */
  public static @Nullable TreePath nextVisibleSibling(@NotNull JTree tree, @Nullable TreePath lead) {
    TreePath parent = lead == null ? null : lead.getParentPath();
    return parent == null ? null : nextVisiblePath(tree, lead, path -> parent.equals(path.getParentPath()));
  }

  /**
   * @param tree      a tree, which nodes should be iterated
   * @param path      a starting tree path
   * @param predicate a predicate that allows to skip some paths
   * @return {@code null} if next visible path cannot be found
   */
  public static @Nullable TreePath nextVisiblePath(@NotNull JTree tree, TreePath path, @NotNull Predicate<? super TreePath> predicate) {
    return nextVisiblePath(tree, tree.getRowForPath(path), predicate);
  }

  /**
   * @param tree      a tree, which nodes should be iterated
   * @param row       a starting row number to iterate
   * @param predicate a predicate that allows to skip some paths
   * @return {@code null} if next visible path cannot be found
   */
  public static @Nullable TreePath nextVisiblePath(@NotNull JTree tree, int row, @NotNull Predicate<? super TreePath> predicate) {
    return nextVisiblePath(tree, row, isCyclicScrollingAllowed(), predicate);
  }

  /**
   * @param tree      a tree, which nodes should be iterated
   * @param row       a starting row number to iterate
   * @param cyclic    {@code true} if cyclic searching is allowed, {@code false} otherwise
   * @param predicate a predicate that allows to skip some paths
   * @return {@code null} if next visible path cannot be found
   */
  public static @Nullable TreePath nextVisiblePath(@NotNull JTree tree, int row, boolean cyclic,
                                                   @NotNull Predicate<? super TreePath> predicate) {
    assert EventQueue.isDispatchThread();
    if (row < 0) return null; // ignore illegal row
    int count = tree.getRowCount();
    if (count <= row) return null; // ignore illegal row
    int stop = row;
    while (true) {
      row++; // NB!: increase row before checking for cycle scrolling
      if (row == count && cyclic) row = 0;
      if (row == count) return null; // stop scrolling on last node if no cyclic scrolling
      if (row == stop) return null; // stop scrolling when cyclic scrolling is done
      TreePath path = tree.getPathForRow(row);
      if (path != null && predicate.test(path)) return path;
    }
  }

  /**
   * @param tree a tree, which nodes should be iterated
   * @param lead a starting tree path
   * @return previous tree path with the same parent, or {@code null} if it is not found
   */
  public static @Nullable TreePath previousVisibleSibling(@NotNull JTree tree, @Nullable TreePath lead) {
    TreePath parent = lead == null ? null : lead.getParentPath();
    return parent == null ? null : previousVisiblePath(tree, lead, path -> parent.equals(path.getParentPath()));
  }

  /**
   * @param tree      a tree, which nodes should be iterated
   * @param path      a starting tree path
   * @param predicate a predicate that allows to skip some paths
   * @return {@code null} if previous visible path cannot be found
   */
  public static @Nullable TreePath previousVisiblePath(@NotNull JTree tree, TreePath path, @NotNull Predicate<? super TreePath> predicate) {
    return previousVisiblePath(tree, tree.getRowForPath(path), predicate);
  }

  /**
   * @param tree      a tree, which nodes should be iterated
   * @param row       a starting row number to iterate
   * @param predicate a predicate that allows to skip some paths
   * @return {@code null} if previous visible path cannot be found
   */
  public static @Nullable TreePath previousVisiblePath(@NotNull JTree tree, int row, @NotNull Predicate<? super TreePath> predicate) {
    return previousVisiblePath(tree, row, isCyclicScrollingAllowed(), predicate);
  }

  /**
   * @param tree      a tree, which nodes should be iterated
   * @param row       a starting row number to iterate
   * @param cyclic    {@code true} if cyclic searching is allowed, {@code false} otherwise
   * @param predicate a predicate that allows to skip some paths
   * @return {@code null} if previous visible path cannot be found
   */
  public static @Nullable TreePath previousVisiblePath(@NotNull JTree tree, int row, boolean cyclic,
                                                       @NotNull Predicate<? super TreePath> predicate) {
    assert EventQueue.isDispatchThread();
    if (row < 0) return null; // ignore illegal row
    int count = tree.getRowCount();
    if (count <= row) return null; // ignore illegal row
    int stop = row;
    while (true) {
      if (row == 0 && cyclic) row = count;
      if (row == 0) return null; // stop scrolling on first node if no cyclic scrolling
      row--; // NB!: decrease row after checking for cyclic scrolling
      if (row == stop) return null; // stop scrolling when cyclic scrolling is done
      TreePath path = tree.getPathForRow(row);
      if (path != null && predicate.test(path)) return path;
    }
  }

  /**
   * @return {@code true} if cyclic scrolling in trees is allowed, {@code false} otherwise
   */
  public static boolean isCyclicScrollingAllowed() {
    if (ScreenReader.isActive()) return false;
    if (!Registry.is("ide.tree.ui.cyclic.scrolling.allowed")) return false;
    UISettings settings = UISettings.getInstanceOrNull();
    return settings != null && settings.getCycleScrolling();
  }

  /**
   * @param tree  a tree, which paths should be requested
   * @param row   an index of a visible tree path
   * @param count an expected amount of visible tree paths
   * @return a requested tree path
   */
  private static @NotNull TreePath getVisiblePathWithValidation(@NotNull JTree tree, int row, int count) {
    if (count != tree.getRowCount()) throw new ConcurrentModificationException("tree is modified");
    TreePath path = tree.getPathForRow(row);
    if (path == null) throw new NullPointerException("path is not found at row " + row);
    return path;
  }
}
