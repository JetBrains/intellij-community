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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.model.HierarchicalArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementMatcherSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.util.containers.Stack;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Contains various utility methods to be used during showing arrangement settings.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 9:14 AM
 */
public class ArrangementConfigUtil {

  private ArrangementConfigUtil() {
  }

  /**
   * Allows to answer what new settings are available for a particular {@link ArrangementMatcherSettings arrangement matcher rules}.
   *
   * @param filter    filter to use
   * @param settings  object that encapsulates information about current arrangement matcher settings
   * @return          map which contains information on what new new settings are available at the current situation
   */
  @NotNull
  public static Map<ArrangementSettingType, Collection<?>> buildAvailableOptions(@NotNull ArrangementStandardSettingsAware filter,
                                                                                 @Nullable ArrangementSettingsNode settings)
  {
    Map<ArrangementSettingType, Collection<?>> result = new EnumMap<ArrangementSettingType, Collection<?>>(ArrangementSettingType.class);
    processData(filter, settings, result, ArrangementSettingType.TYPE, ArrangementEntryType.values());
    processData(filter, settings, result, ArrangementSettingType.MODIFIER, ArrangementModifier.values());
    return result;
  }

  private static <T> void processData(@NotNull ArrangementStandardSettingsAware filter,
                                      @Nullable ArrangementSettingsNode settings,
                                      @NotNull Map<ArrangementSettingType, Collection<?>> result,
                                      @NotNull ArrangementSettingType type,
                                      @NotNull T[] values)
  {
    List<T> data = null;
    for (T v : values) {
      if (!isEnabled(v, filter, settings)) {
        continue;
      }
      if (data == null) {
        data = new ArrayList<T>();
      }
      data.add(v);
    }
    if (data != null) {
      result.put(type, data);
    }
  }

  public static boolean isEnabled(@NotNull Object conditionId,
                                  @NotNull ArrangementStandardSettingsAware filter,
                                  @Nullable ArrangementSettingsNode settings)
  {
    if (conditionId instanceof ArrangementEntryType) {
      return filter.isEnabled((ArrangementEntryType)conditionId, settings);
    }
    else if (conditionId instanceof ArrangementModifier) {
      return filter.isEnabled((ArrangementModifier)conditionId, settings);
    }
    else {
      return false;
    }
  }
  
  @Nullable
  public static Point getLocationOnScreen(@NotNull JComponent component) {
    int dx = 0;
    int dy = 0;
    for (Container c = component; c != null; c = c.getParent()) {
      if (c.isShowing()) {
        Point locationOnScreen = c.getLocationOnScreen();
        locationOnScreen.translate(dx, dy);
        return locationOnScreen;
      }
      else {
        Point location = c.getLocation();
        dx += location.x;
        dy += location.y;
      }
    }
    return null;
  }

  public static int getDepth(@NotNull HierarchicalArrangementSettingsNode node) {
    HierarchicalArrangementSettingsNode child = node.getChild();
    return child == null ? 1 : 1 + getDepth(child);
  }

  @NotNull
  public static HierarchicalArrangementSettingsNode getLast(@NotNull HierarchicalArrangementSettingsNode node) {
    HierarchicalArrangementSettingsNode result = node;
    for (HierarchicalArrangementSettingsNode child = node.getChild(); child != null; child = child.getChild()) {
      result = child;
    }
    return result;
  }

  @NotNull
  public static ArrangementTreeNode getLastBefore(@NotNull ArrangementTreeNode start, @NotNull ArrangementTreeNode stop)
    throws IllegalArgumentException
  {
    ArrangementTreeNode result = start;
    for (ArrangementTreeNode n = start.getParent(); n != stop; n = n.getParent()) {
      if (n == null) {
        throw new IllegalArgumentException(String.format(
          "Non-crossing paths detected - start: %s, stop: %s", new TreePath(start), new TreePath(stop)
        ));
      }
      result = n;
    }
    return result;
  }

  @NotNull
  public static ArrangementTreeNode getLast(@NotNull final ArrangementTreeNode node) {
    ArrangementTreeNode result = node;
    int childCount = result.getChildCount();
    while (childCount > 0) {
      result = result.getChildAt(childCount - 1);
    }
    return result;
  }
  
  public static int distance(@NotNull TreeNode parent, @NotNull TreeNode child) {
    if (parent == child) {
      return 1;
    }
    int result = 1;
    for (TreeNode n = child; n != null && n != parent; n = n.getParent()) {
      result++;
    }
    return result;
  }

  /**
   * @param uiParentNode UI tree node which should hold UI nodes created for representing given settings node;
   *                     <code>null</code> as an indication that we want to create a standalone nodes hierarchy
   * @param settingsNode settings node which should be represented at the UI tree denoted by the given UI tree node
   * @param model        tree model to use for the tree modification
   * @return             pair {@code (bottom-most leaf node created; number of rows created)}
   */
  @NotNull
  public static Pair<ArrangementTreeNode, Integer> map(@Nullable ArrangementTreeNode uiParentNode,
                                                       @NotNull HierarchicalArrangementSettingsNode settingsNode,
                                                       @Nullable DefaultTreeModel model)
  {
    ArrangementTreeNode uiNode = null;
    int rowsCreated = 0;
    if (uiParentNode != null && uiParentNode.getChildCount() > 0) {
      ArrangementTreeNode child = uiParentNode.getChildAt(uiParentNode.getChildCount() - 1);
      if (settingsNode.getCurrent().equals(child.getBackingSetting())) {
        uiNode = child;
      }
    }
    if (uiNode == null) {
      uiNode = new ArrangementTreeNode(settingsNode.getCurrent());
      if (uiParentNode != null) {
        if (model == null) {
          uiParentNode.add(uiNode);
        }
        else {
          model.insertNodeInto(uiNode, uiParentNode, uiParentNode.getChildCount());
        }
      }
      rowsCreated++;
    }
    ArrangementTreeNode leaf = uiNode;
    HierarchicalArrangementSettingsNode childSettingsNode = settingsNode.getChild();
    if (childSettingsNode != null) {
      Pair<ArrangementTreeNode, Integer> pair = map(uiNode, childSettingsNode, model);
      leaf = pair.first;
      rowsCreated += pair.second;
    }
    return Pair.create(leaf, rowsCreated);
  }

  /**
   * Utility method which helps to replace node sub-hierarchy identified by the given start and end nodes (inclusive) by
   * a sub-hierarchy which is denoted by the given root. 
   * 
   * @param from         indicates start of the node sub-hierarchy (top-most node) to be replaced (inclusive)
   * @param to           indicates end of the node sub-hierarchy (bottom-most node) to be replaced (inclusive)
   * @param replacement  root of the node sub-hierarchy which should replace the one identified by the given 'start' and 'end' nodes
   * @param treeModel    model which should hold ui nodes
   * @param rootVisible  determines if the root should be count during rows calculations
   * @return             collection of row changes at the form {@code 'old row -> new row'}
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  @NotNull
  public static TIntIntHashMap replace(@NotNull ArrangementTreeNode from,
                                       @NotNull ArrangementTreeNode to,
                                       @NotNull ArrangementTreeNode replacement,
                                       @NotNull DefaultTreeModel treeModel,
                                       boolean rootVisible)
  {
    markRows(from, rootVisible);
    if (from == to) {
      ArrangementTreeNode parent = from.getParent();
      int index = parent.getIndex(from);
      treeModel.removeNodeFromParent(from);
      insert(parent, index, replacement, treeModel);
      return collectRowChangesAndUnmark(parent, rootVisible);
    }

    // The algorithm looks as follows:
    //   1. Cut sub-hierarchy which belongs to the given 'from' root and is located below the 'to -> from' path;
    //   2. Remove 'to -> from' sub-hierarchy' by going bottom-up and stopping as soon as a current node has a child over than one
    //      from the sub-hierarchy to remove;
    //   3. Add 'replacement' sub-hierarchy starting after the 'from' index at its parent;
    //   4. Add sub-hierarchy cut at the 1) starting after the 'replacement' sub-hierarchy index;
    // Example:
    //   Initial:
    //      0
    //      |_1
    //        |_2
    //        | |_3
    //        | |_4
    //        | |_5
    //        |
    //        |_6
    //   Let's say we want to replace the sub-hierarchy '1 -> 2 -> 4' by the sub-hierarchy '1 -> 4'. The algorithm in action:
    //     1. Cut bottom sub-hierarchy:
    //         Current:      Cut:
    //           0            1
    //           |_1          |_2
    //             |_2        | |_5
    //               |_3      |
    //               |_4      |_6
    //
    //     2. Remove target sub-hierarchy:
    //         Current:  
    //           0       
    //           |_1     
    //             |_2    <-- stop at this node because it has a child node '3' which doesn't belong to the '1 -> 2 -> 4'
    //               |_3 
    //     3. Add 'replacement' sub-hierarchy:
    //         Current:  
    //           0       
    //           |_1    <-- re-use this node for '1 -> 4' addition
    //             |_2    
    //             | |_3
    //             |
    //             |_4
    //     4. Add 'bottom' sub-hierarchy:
    //         Current:  
    //           0       
    //           |_1    <-- re-use this node either for '1 -> 2 -> 5' or '1 -> 6' addition
    //             |_2  
    //             | |_3
    //             |
    //             |_4
    //             |
    //             |_2
    //             | |_5
    //             |
    //             |_6
    //
    // Note: we need to have a notion of 'equal nodes' for node re-usage. It's provided by comparing node user objects.

    final ArrangementTreeNode root = from.getParent();

    //region Cut bottom sub-hierarchy
    ArrangementTreeNode cutHierarchy = null;
    for (ArrangementTreeNode current = to; current != root; current = current.getParent()) {
      ArrangementTreeNode parent = current.getParent();
      int i = parent.getIndex(current);
      int childCount = parent.getChildCount();
      if (i >= childCount - 1) {
        continue;
      }
      ArrangementTreeNode parentCopy = parent.copy();
      if (parent.getChildCount() > 0) {
        parentCopy.resetRow();
      }
      if (cutHierarchy != null) {
        parentCopy.add(cutHierarchy);
      }
      for (int j = i + 1; j < childCount; j++) {
        ArrangementTreeNode child = parent.getChildAt(j);
        treeModel.removeNodeFromParent(child);
        parentCopy.add(child);
      }
      cutHierarchy = parentCopy;
    }
    //endregion

    int childCountBefore = root.getChildCount();
    
    //region Remove target sub-hierarchy
    for (ArrangementTreeNode current = to; current != root;) {
      ArrangementTreeNode parent = current.getParent();
      treeModel.removeNodeFromParent(current);
      current = parent;
      if (parent.getChildCount() > 0) {
        break;
      }
    }
    //endregion

    //region Insert nodes.
    int insertionIndex = root.getChildCount() < childCountBefore ? childCountBefore - 1 : childCountBefore;
    boolean merged = insert(root, insertionIndex, replacement, treeModel);
    if (cutHierarchy != null) {
      List<ArrangementTreeNode> toInsert = new ArrayList<ArrangementTreeNode>();
      if (hasEqualSetting(root, cutHierarchy)) {
        for (int i = 0; i < cutHierarchy.getChildCount(); i++) {
          toInsert.add(cutHierarchy.getChildAt(i));
        }
      }
      else {
        toInsert.add(cutHierarchy);
      }
      for (ArrangementTreeNode node : toInsert) {
        insert(root, root.getChildCount(), node, treeModel);
      }
    }
    //endregion
    
    return collectRowChangesAndUnmark(root, rootVisible);
  }

  /**
   * Enriches every node at the hierarchy denoted by the given node by information about it's row. 
   * 
   * @param node         reference to the target hierarchy
   * @param rootVisible  determines if the root should be count during rows calculations
   */
  private static void markRows(@NotNull ArrangementTreeNode node, boolean rootVisible) {
    ArrangementTreeNode root = getRoot(node);
    int row = rootVisible ? 0 : -1;
    Stack<ArrangementTreeNode> nodes = new Stack<ArrangementTreeNode>();
    nodes.push(root);
    while (!nodes.isEmpty()) {
      ArrangementTreeNode n = nodes.pop();
      n.markRow(row++);
      for (int i = n.getChildCount() - 1; i >= 0; i--) {
        nodes.push(n.getChildAt(i));
      }
    }
  }

  @NotNull
  public static ArrangementTreeNode getRoot(ArrangementTreeNode node) {
    ArrangementTreeNode root = node;
    for (ArrangementTreeNode n = root; n != null; n = n.getParent()) {
      root = n;
    }
    return root;
  }

  /**
   * Processes hierarchy denoted by the given node assuming that every node there contains information about its initial row
   * (see {@link #markRows(ArrangementTreeNode, boolean)}).
   * <p/>
   * Collects all row changes and returns them. All row information is dropped from the nodes during the current method processing.
   * 
   * @param node  reference to the target nodes hierarchy
   * @return      collection of row changes at the form {@code 'old row -> new row'}
   */
  @NotNull
  private static TIntIntHashMap collectRowChangesAndUnmark(@NotNull ArrangementTreeNode node, boolean rootVisible) {
    @NotNull TIntIntHashMap changes = new TIntIntHashMap();
    ArrangementTreeNode root = getRoot(node);
    int row = rootVisible ? 0 : -1;
    Stack<ArrangementTreeNode> nodes = new Stack<ArrangementTreeNode>();
    nodes.push(root);
    while (!nodes.isEmpty()) {
      ArrangementTreeNode n = nodes.pop();
      if (n.isRowSet() && n.getRow() != row) {
        changes.put(n.getRow(), row);
      }
      n.resetRow();
      row++;
      for (int i = n.getChildCount() - 1; i >= 0; i--) {
        nodes.push(n.getChildAt(i));
      }
    }
    return changes;
  }

  /**
   * Allows to map given node to its row at the hierarchy.
   * 
   * @param node         target node
   * @param rootVisible  determines if the root should be count on rows calculation
   * @return             given node's row at the nodes hierarchy (0-based)
   */
  public static int getRow(@NotNull ArrangementTreeNode node, boolean rootVisible) {
    ArrangementTreeNode root = getRoot(node);
    int row = rootVisible ? 0 : -1;
    Stack<ArrangementTreeNode> nodes = new Stack<ArrangementTreeNode>();
    nodes.push(root);
    while (!nodes.isEmpty()) {
      ArrangementTreeNode n = nodes.pop();
      if (n == node) {
        return row;
      }
      row++;
      for (int i = n.getChildCount() - 1; i >= 0; i--) {
        nodes.push(n.getChildAt(i));
      }
    }
    
    StringBuilder buffer = new StringBuilder();
    String separator = "->";
    for (TreeNode n = node; n != null; n = n.getParent()) {
      buffer.append(n).append(separator);
    }
    buffer.setLength(buffer.length() - separator.length());
    throw new RuntimeException("Invalid ArrangementTreeNode detected: " + buffer.toString());
  }
  
  /**
   * Inserts given child to the given parent re-using existing nodes under the parent sub-hierarchy if possible.
   * <p/>
   * Example:
   * <pre>
   *   parent:  0         to-insert: 2     
   *            |_1                  |_3   
   *            |_2                    |_6 
   *            | |_3                      
   *            |   |_4                    
   *            |_5                        
   *   -------------------------------------------------------------------------------------------------
   *  | index:  |       0             |       1             |       2             |       3             |
   *  |-------------------------------------------------------------------------------------------------
   *  | result: |       0             |       0             |       0             |       0             |
   *  |         |       |_2           |       |_1           |       |_1           |       |_1           |
   *  |         |       | |_3         |       |_2           |       |_2           |       |_2           |
   *  |         |       |   |_6       |       | |_3         |       | |_3         |       | |_3         |
   *  |         |       |_1           |       |   |_6       |       |   |_4       |       |   |_4       |
   *  |         |       |_2           |       |   |_4       |       |   |_6       |       |_5           |
   *  |         |       | |_3         |       |_5           |       |_5           |       |_2           |
   *  |         |       |   |_4       |                     |                     |         |_3         |
   *  |         |       |_5           |                     |                     |           |_6       |
   * </pre>
   * <p/>
   * 
   * @param parent     parent node to insert into
   * @param index      insertion index to use for the given parent node
   * @param child      node to insert to the given parent node at the given insertion index
   * @param treeModel  model which should hold UI nodes
   * @return           <code>true</code> if given child node has been merged to the existing node; <code>false</code> otherwise
   */
  public static boolean insert(@NotNull final ArrangementTreeNode parent,
                               final int index,
                               @NotNull final ArrangementTreeNode child,
                               @NotNull DefaultTreeModel treeModel)
  {
    if (parent.getChildCount() < index) {
      treeModel.insertNodeInto(child, parent, parent.getChildCount());
      return false;
    }

    if (child.getChildCount() <= 0) {
      // Don't merge the last child.
      treeModel.insertNodeInto(child, parent, index);
      return false;
    }

    boolean anchorAbove = false;
    ArrangementTreeNode mergeCandidate = null;
    if (index > 0) {
      mergeCandidate = parent.getChildAt(index - 1);
      if (mergeCandidate.getChildCount() <= 0 /* don't merge into leaf node*/ || !hasEqualSetting(mergeCandidate, child)) {
        mergeCandidate = null;
      }
    }

    if (index < parent.getChildCount()) {
      ArrangementTreeNode n = parent.getChildAt(index);
      if (hasEqualSetting(n, child)) {
        mergeCandidate = n;
        anchorAbove = true;
      }
    }

    if (mergeCandidate == null) {
      treeModel.insertNodeInto(child, parent, index);
      return false;
    }

    for (int i = 0, limit = child.getChildCount(); i < limit; i++) {
      insert(mergeCandidate, anchorAbove ? 0 : mergeCandidate.getChildCount(), child.getChildAt(0), treeModel);
    }
    return true;
  }

  private static boolean hasEqualSetting(@NotNull ArrangementTreeNode node1, @NotNull ArrangementTreeNode node2) {
    ArrangementSettingsNode setting1 = node1.getBackingSetting();
    ArrangementSettingsNode setting2 = node2.getBackingSetting();
    if (setting1 == null || setting2 == null) {
      return false;
    }
    return setting1.equals(setting2);
  }
}
