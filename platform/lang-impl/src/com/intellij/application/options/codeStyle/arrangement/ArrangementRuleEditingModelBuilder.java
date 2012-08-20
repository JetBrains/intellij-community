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
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.model.HierarchicalArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementSettingsGrouper;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsRepresentationAware;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

/**
 * Holds glue logic between arrangement settings and their representation -
 * '{@link ArrangementSettingsNode} -&gt; {@link ArrangementRuleEditingModel}'
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 8/15/12 2:50 PM
 */
public class ArrangementRuleEditingModelBuilder {

  /**
   * Does the following:
   * <pre>
   * <ol>
   *   <li>
   *     {@link HierarchicalArrangementSettingsNode Groups} given {@link ArrangementSettingsNode settings} using
   *     the given {@link ArrangementSettingsGrouper#group(ArrangementSettingsNode) strategy};
   *   </li>
   *   <li>
   *     Build {@link DefaultMutableTreeNode tree nodes} for the {@link HierarchicalArrangementSettingsNode groiping-aware nodes}
   *     and register them within the target tree structure (denoted by the given settings root element);
   *   </li>
   *   <li>
   *     Build necessary {@link ArrangementRuleEditingModel editing models} and store them at the given container (a key is a node row);
   *   </li>
   * </ol>
   * </pre>
   *
   * @param setting     target settings to process
   * @param tree        UI tree which shows arrangement matcher rules
   * @param root        UI tree settings root to use (may be not the same as the tree root)
   * @param grouper     strategy that knows how to
   *                    {@link ArrangementStandardSettingsRepresentationAware#getDisplayValue(ArrangementModifier) group} setting
   *                    nodes for UI representation
   * @param rowMappings container to hold built {@link ArrangementRuleEditingModel editing models} (UI tree row numbers are used as keys)
   */
  @SuppressWarnings("MethodMayBeStatic")
  public void build(@NotNull ArrangementSettingsNode setting,
                    @NotNull JTree tree,
                    @NotNull ArrangementTreeNode root,
                    @NotNull ArrangementSettingsGrouper grouper,
                    @NotNull TIntObjectHashMap<ArrangementRuleEditingModelImpl> rowMappings)
  {
    int initialInsertRow = 0;
    
    // Count rows before the settings root.
    for (TreeNode n = root.getParent(); n != null; n = n.getParent()) {
      for (int i = n.getChildCount() - 1; i >= 0; i--) {
        TreeNode child = n.getChildAt(i);
        if (child != root) {
          initialInsertRow += calculateRowsCount(child);
        }
      }
      initialInsertRow++;
    }
    
    // Count root width.
    initialInsertRow += calculateRowsCount(root);
    if (!tree.isRootVisible()) {
      initialInsertRow--;
    }

    HierarchicalArrangementSettingsNode grouped = grouper.group(setting);
    DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
    Pair<ArrangementTreeNode, Integer> pair = ArrangementConfigUtil.map(root, grouped);
    ArrangementTreeNode topMostNode = ArrangementConfigUtil.getLastBefore(pair.first, root);
    int row = initialInsertRow + pair.second - 1;
    ArrangementRuleEditingModelImpl model = new ArrangementRuleEditingModelImpl(
      treeModel,
      setting,
      topMostNode,
      pair.first,
      grouper,
      rowMappings,
      row,
      tree.isRootVisible() ? 0 : -1
    );
    rowMappings.put(row, model);
  }

  private static int calculateRowsCount(@NotNull TreeNode node) {
    int result = 1;
    for (int i = node.getChildCount() - 1; i >= 0; i--) {
      result += calculateRowsCount(node.getChildAt(i));
    }
    return result;
  }
}
