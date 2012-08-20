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
import javax.swing.tree.DefaultMutableTreeNode;
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
  public static TreeNode getLastBefore(@NotNull TreeNode start, @NotNull TreeNode stop) throws IllegalArgumentException {
    TreeNode result = start;
    for (TreeNode n = start.getParent(); n != stop; n = n.getParent()) {
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
  public static DefaultMutableTreeNode getLast(@NotNull final DefaultMutableTreeNode node) {
    TreeNode result = node;
    int childCount = result.getChildCount();
    while (childCount > 0) {
      result = result.getChildAt(childCount - 1);
    }
    return (DefaultMutableTreeNode)result;
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
   * @return             pair {@code (bottom-most leaf node created; number of rows created)}
   */
  @NotNull
  public static Pair<DefaultMutableTreeNode, Integer> map(@Nullable DefaultMutableTreeNode uiParentNode,
                                                          @NotNull HierarchicalArrangementSettingsNode settingsNode)
  {
    DefaultMutableTreeNode uiNode = null;
    int rowsCreated = 0;
    if (uiParentNode != null) {
      for (int i = uiParentNode.getChildCount() - 1; i >= 0; i--) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode)uiParentNode.getChildAt(i);
        if (settingsNode.getCurrent().equals(child.getUserObject())) {
          uiNode = child;
          break;
        }
      }
    }
    if (uiNode == null) {
      uiNode = new DefaultMutableTreeNode(settingsNode.getCurrent());
      if (uiParentNode != null) {
        uiParentNode.add(uiNode);
      }
      rowsCreated++;
    }
    DefaultMutableTreeNode leaf = uiNode;
    HierarchicalArrangementSettingsNode childSettingsNode = settingsNode.getChild();
    if (childSettingsNode != null) {
      Pair<DefaultMutableTreeNode, Integer> pair = map(uiNode, childSettingsNode);
      leaf = pair.first;
      rowsCreated += pair.second;
    }
    return Pair.create(leaf, rowsCreated);
  }

  /**
   * Utility method which helps to replace node sub-hierarchy identified by the given start and end nodes (inclusive) by
   * a sub-hierarchy which is denoted by the given root. 
   * 
   * @param from         indicates start of the node sub-hierarchy to be replaced (inclusive)
   * @param to           indicates end of the node sub-hierarchy to be replaced (inclusive)
   * @param replacement  root of the node sub-hierarchy which should replace the one identified by the given 'start' and 'end' nodes
   * @return             collection of row changes at the form {@code 'old row -> new row'}
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  @NotNull
  public static TIntIntHashMap replace(@NotNull DefaultMutableTreeNode from,
                                       @NotNull DefaultMutableTreeNode to,
                                       @NotNull DefaultMutableTreeNode replacement)
  {
    markRows(from);
    if (from == to) {
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode)from.getParent();
      int index = parent.getIndex(from);
      parent.remove(index);
      parent.insert(replacement, index);
      return collectRowChangesAndUnmark(parent);
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

    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)from.getParent();

    //region Cut bottom sub-hierarchy
    DefaultMutableTreeNode cutHierarchy = null;
    for (DefaultMutableTreeNode current = to; current != root; current = (DefaultMutableTreeNode)current.getParent()) {
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode)current.getParent();
      int i = parent.getIndex(current);
      if (i >= parent.getChildCount() - 1) {
        continue;
      }
      Object userObject = parent.getUserObject();
      if (userObject instanceof RowInfo) {
        userObject = ((RowInfo)userObject).userObject;
      }
      DefaultMutableTreeNode parentCopy = new DefaultMutableTreeNode(userObject);
      if (cutHierarchy != null) {
        parentCopy.add(cutHierarchy);
      }
      for (int j = i + 1, limit = parent.getChildCount(); j < limit; j++) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode)parent.getChildAt(j);
        parent.remove(j);
        parentCopy.add(child);
      }
      cutHierarchy = parentCopy;
    }
    //endregion
    
    int insertionIndex = root.getIndex(from) + 1; 
    
    //region Remove target sub-hierarchy
    for (DefaultMutableTreeNode current = to; current != root;) {
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode)current.getParent();
      parent.remove(current);
      current = parent;
      if (parent.getChildCount() > 0) {
        break;
      }
    }
    //endregion

    //region Insert nodes.
    boolean merged = insert(root, insertionIndex, replacement);
    if (cutHierarchy != null) {
      insert(root, insertionIndex + (merged ? 0 : 1), cutHierarchy);
    }
    //endregion
    
    return collectRowChangesAndUnmark(root);
  }

  /**
   * Enriches every node at the hierarchy denoted by the given node by information about it's row. 
   * 
   * @param node  reference to the target hierarchy
   */
  private static void markRows(@NotNull DefaultMutableTreeNode node) {
    DefaultMutableTreeNode root = getRoot(node);
    int row = 0;
    Stack<DefaultMutableTreeNode> nodes = new Stack<DefaultMutableTreeNode>();
    nodes.push(root);
    while (!nodes.isEmpty()) {
      DefaultMutableTreeNode n = nodes.pop();
      n.setUserObject(new RowInfo(row++, n.getUserObject()));
      for (int i = n.getChildCount() - 1; i >= 0; i--) {
        nodes.push((DefaultMutableTreeNode)n.getChildAt(i));
      }
    }
  }

  @NotNull
  public static DefaultMutableTreeNode getRoot(DefaultMutableTreeNode node) {
    DefaultMutableTreeNode root = node;
    for (TreeNode n = root; n != null; n = n.getParent()) {
      root = (DefaultMutableTreeNode)n;
    }
    return root;
  }

  /**
   * Processes hierarchy denoted by the given node assuming that every node there contains information about its initial row
   * (see {@link #markRows(DefaultMutableTreeNode)}).
   * <p/>
   * Collects all row changes and returns them. All row information is dropped from the nodes during the current method processing.
   * 
   * @param node  reference to the target nodes hierarchy
   * @return      collection of row changes at the form {@code 'old row -> new row'}
   */
  @NotNull
  private static TIntIntHashMap collectRowChangesAndUnmark(@NotNull DefaultMutableTreeNode node) {
    @NotNull TIntIntHashMap changes = new TIntIntHashMap();
    DefaultMutableTreeNode root = getRoot(node);
    int row = 0;
    Stack<DefaultMutableTreeNode> nodes = new Stack<DefaultMutableTreeNode>();
    nodes.push(root);
    while (!nodes.isEmpty()) {
      DefaultMutableTreeNode n = nodes.pop();
      Object userObject = n.getUserObject();
      if (userObject instanceof RowInfo) {
        RowInfo rowInfo = (RowInfo)userObject;
        if (rowInfo.row != row) {
          changes.put(rowInfo.row, row);
        }
        n.setUserObject(rowInfo.userObject);
      }
      row++;
      for (int i = n.getChildCount() - 1; i >= 0; i--) {
        nodes.push((DefaultMutableTreeNode)n.getChildAt(i));
      }
    }
    return changes;
  }

  /**
   * Allows to map given node to its row at the hierarchy.
   * 
   * @param node  target node
   * @return      given node's row at the nodes hierarchy (0-based)
   */
  public static int getRow(@NotNull DefaultMutableTreeNode node) {
    DefaultMutableTreeNode root = getRoot(node);
    int row = 0;
    Stack<DefaultMutableTreeNode> nodes = new Stack<DefaultMutableTreeNode>();
    nodes.push(root);
    while (!nodes.isEmpty()) {
      DefaultMutableTreeNode n = nodes.pop();
      if (n == node) {
        return row;
      }
      row++;
      for (int i = n.getChildCount() - 1; i >= 0; i--) {
        nodes.push((DefaultMutableTreeNode)n.getChildAt(i));
      }
    }
    
    StringBuilder buffer = new StringBuilder();
    String separator = "->";
    for (TreeNode n = node; n != null; n = n.getParent()) {
      buffer.append(n).append(separator);
    }
    buffer.setLength(buffer.length() - separator.length());
    throw new RuntimeException("Invalid DefaultMutableTreeNode detected: " + buffer.toString());
  }
  
  /**
   * Inserts given child to the given parent re-using existing nodes under the parent sub-hierarchy if possible (two nodes are
   * considered equals if their {@link DefaultMutableTreeNode#getUserObject() user objects} are equal.
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
   * @param parent  parent node to insert into
   * @param index   insertion index to use for the given parent node
   * @param child   node to insert to the given parent node at the given insertion index
   * @return        <code>true</code> if given child node has been merged to the existing node; <code>false</code> otherwise
   */
  public static boolean insert(@NotNull final DefaultMutableTreeNode parent, final int index, @NotNull final DefaultMutableTreeNode child) {
    if (parent.getChildCount() < index) {
      parent.add(child);
      return false;
    }
    
    boolean anchorAbove = false;
    DefaultMutableTreeNode mergeCandidate = null;
    if (index > 0) {
      mergeCandidate = (DefaultMutableTreeNode)parent.getChildAt(index - 1);
      if (!userDataEqual(mergeCandidate.getUserObject(), child.getUserObject())) {
        mergeCandidate = null;
      }
    }

    if (index < parent.getChildCount()) {
      DefaultMutableTreeNode n = (DefaultMutableTreeNode)parent.getChildAt(index);
      if (userDataEqual(n.getUserObject(), child.getUserObject())) {
        mergeCandidate = n;
        anchorAbove = true;
      }
    }

    if (mergeCandidate == null) {
      if (index < parent.getChildCount()) {
        parent.insert(child, index);
      }
      else {
        parent.add(child);
      }
      return false;
    }

    for (int i = 0, limit = child.getChildCount(); i < limit; i++) {
      insert(mergeCandidate, anchorAbove ? 0 : mergeCandidate.getChildCount(), (DefaultMutableTreeNode)child.getChildAt(0));
    }
    return true;
  }

  private static boolean userDataEqual(@Nullable Object first, @Nullable Object second) {
    if (first == null && second == null) {
      return true;
    }
    else if (first == null ^ second == null) {
      return false;
    }
    Object effectiveFirst = first instanceof RowInfo ? ((RowInfo)first).userObject : first;
    Object effectiveSecond = second instanceof RowInfo ? ((RowInfo)second).userObject : second;
    return effectiveFirst.equals(effectiveSecond);
  }
  
  private static class RowInfo {
    @Nullable
    public final Object userObject;
    public final int    row;

    RowInfo(int row, @Nullable Object data) {
      userObject = data;
      this.row = row;
    }

    @Override
    public String toString() {
      return "row=" + row + (userObject == null ? "" : ": " + userObject.toString());
    }
  }
}
