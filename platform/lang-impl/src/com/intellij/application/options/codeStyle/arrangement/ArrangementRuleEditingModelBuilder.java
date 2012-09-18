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
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.HierarchicalArrangementConditionNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsRepresentationAware;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.Set;

/**
 * Holds glue logic between arrangement settings and their representation -
 * '{@link ArrangementMatchCondition} -&gt; {@link ArrangementRuleEditingModel}'
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
   *     {@link HierarchicalArrangementConditionNode Groups} given {@link ArrangementMatchCondition settings} using
   *     the given grouping rules;
   *   </li>
   *   <li>
   *     Build {@link DefaultMutableTreeNode tree nodes} for the {@link HierarchicalArrangementConditionNode groiping-aware nodes}
   *     and register them within the target tree structure (denoted by the given settings root element);
   *   </li>
   *   <li>
   *     Build necessary {@link ArrangementRuleEditingModel editing models} and store them at the given container (a key is a node row);
   *   </li>
   * </ol>
   * </pre>
   *
   * @param rule           target rule to process
   * @param tree           UI tree which shows arrangement matcher rules
   * @param root           UI tree settings root to use (may be not the same as the tree root)
   * @param anchor         node after which should be previous sibling for the root node of the inserted condition;
   *                       <code>null</code> as an indication that new condition nodes should be inserted as the last 'root' child
   * @param groupingRules  conditions grouping rules
   *                       {@link ArrangementStandardSettingsRepresentationAware#getDisplayValue(ArrangementModifier) group} setting
   *                       nodes for UI representation
   * @return               created model and collection of row changes at the form {@code 'old row -> new row'} (all rows are zero-based);
   *                       <code>null</code> if no model has been created
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public Pair<ArrangementRuleEditingModelImpl, TIntIntHashMap> build(
    @NotNull StdArrangementMatchRule rule,
    @NotNull JTree tree,
    @NotNull ArrangementTreeNode root,
    @Nullable ArrangementTreeNode anchor,
    @NotNull List<Set<ArrangementMatchCondition>> groupingRules)
  {
    HierarchicalArrangementConditionNode grouped = ArrangementUtil.group(rule.getMatcher().getCondition(), groupingRules);
    DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();

    if (grouped.getChild() == null) {
      ArrangementTreeNode lastChild = null;
      if (root.getChildCount() > 0) {
        lastChild = root.getLastChild();
      }
      if (lastChild != null && grouped.getCurrent().equals(lastChild.getBackingCondition())) {
        return null;
      }
    }
    
    Pair<ArrangementTreeNode, Integer> pair = ArrangementConfigUtil.map(null, grouped, null);
    ArrangementTreeNode topMostNode = ArrangementConfigUtil.getRoot(pair.first);
    ArrangementConfigUtil.markRows(root, tree.isRootVisible());
    ArrangementTreeNode bottomHierarchy = null;
    if (anchor != null) {
      bottomHierarchy = ArrangementConfigUtil.cutSubHierarchy(root, treeModel, anchor);
    }
    ArrangementConfigUtil.insert(root, root.getChildCount(), topMostNode, treeModel);
    if (bottomHierarchy != null) {
      ArrangementConfigUtil.insert(root, root.getChildCount(), bottomHierarchy, treeModel);
    }
    
    TIntIntHashMap rowChanges = ArrangementConfigUtil.collectRowChangesAndUnmark(root, tree.isRootVisible());
    topMostNode = ArrangementConfigUtil.getLastBefore(pair.first, root);
    int row = ArrangementConfigUtil.getRow(pair.first, tree.isRootVisible());
    ArrangementRuleEditingModelImpl model = new ArrangementRuleEditingModelImpl(
      treeModel,
      rule,
      topMostNode,
      pair.first,
      groupingRules,
      row,
      tree.isRootVisible()
    );
    return Pair.create(model, rowChanges);
  }
}
