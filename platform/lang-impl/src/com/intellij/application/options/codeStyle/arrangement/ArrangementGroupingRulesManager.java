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

import com.intellij.application.options.codeStyle.arrangement.node.ArrangementCheckBoxNode;
import com.intellij.application.options.codeStyle.arrangement.node.ArrangementComboBoxNode;
import com.intellij.application.options.codeStyle.arrangement.node.ArrangementSectionNode;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for representing {@link ArrangementGroupingRule grouping rules} at tree structure.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 9/27/12 2:36 PM
 */
public class ArrangementGroupingRulesManager {

  @NotNull public static final ArrangementGroupingRulesManager INSTANCE = new ArrangementGroupingRulesManager();

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public MutableTreeNode buildAvailableRules(@NotNull ArrangementStandardSettingsAware settingsFilter,
                                             @NotNull ArrangementNodeDisplayManager displayManager)
  {
    ArrangementSectionNode result = new ArrangementSectionNode(ApplicationBundle.message("arrangement.settings.section.groups"));
    String orderLabel = ApplicationBundle.message("arrangement.settings.label.order") + ":";
    for (ArrangementGroupingType groupingType : ArrangementGroupingType.values()) {
      if (!settingsFilter.isEnabled(groupingType, null)) {
        continue;
      }
      final ArrangementCheckBoxNode<ArrangementGroupingType> groupingNode
        = new ArrangementCheckBoxNode<ArrangementGroupingType>(displayManager, groupingType);
      result.add(groupingNode);
      List<ArrangementEntryOrderType> supportedOrderTypes = new ArrayList<ArrangementEntryOrderType>();
      for (ArrangementEntryOrderType orderType : ArrangementEntryOrderType.values()) {
        if (settingsFilter.isEnabled(groupingType, orderType)) {
          supportedOrderTypes.add(orderType);
        }
      }
      if (supportedOrderTypes.isEmpty()) {
        continue;
      }

      final ArrangementComboBoxNode<ArrangementEntryOrderType> orderTypeNode
        = new ArrangementComboBoxNode<ArrangementEntryOrderType>(displayManager, orderLabel, supportedOrderTypes);
      groupingNode.add(orderTypeNode);
      groupingNode.setChangeCallback(new Consumer<ArrangementCheckBoxNode<ArrangementGroupingType>>() {
        @Override
        public void consume(ArrangementCheckBoxNode<ArrangementGroupingType> node) {
          orderTypeNode.setEnabled(groupingNode.isSelected()); 
        }
      });
      groupingNode.setSelected(true);
    }
    return result;
  }

  @SuppressWarnings({"MethodMayBeStatic", "unchecked"})
  public void applyRules(@NotNull List<ArrangementGroupingRule> rules,
                         @NotNull MutableTreeNode groupsRoot,
                         @NotNull DefaultTreeModel model)
  {
    for (int i = 0; i < groupsRoot.getChildCount(); i++) {
      ArrangementCheckBoxNode<?> child = (ArrangementCheckBoxNode<?>)groupsRoot.getChildAt(i);
      child.setSelected(false);
    }
    for (int i = 0; i < rules.size(); i++) {
      ArrangementGroupingRule rule = rules.get(i);
      int index = getIndex(groupsRoot, rule.getRule());
      if (index < 0) {
        return;
      }
      
      ArrangementCheckBoxNode<?> child = (ArrangementCheckBoxNode<?>)groupsRoot.getChildAt(index);
      child.setSelected(true);
      
      if (i != index) {
        model.removeNodeFromParent(child);
        int insertionIndex = i < index ? i : i - 1;
        model.insertNodeInto(child, groupsRoot, insertionIndex);
      }

      if (child.getChildCount() > 0) {
        ArrangementComboBoxNode<ArrangementEntryOrderType> orderTypeNode =
          (ArrangementComboBoxNode<ArrangementEntryOrderType>)child.getChildAt(0);
        orderTypeNode.setSelectedValue(rule.getOrderType());
      }
    }
  }

  private static int getIndex(@NotNull TreeNode root, @NotNull Object data) {
    for (int i = 0; i < root.getChildCount(); i++) {
      TreeNode child = root.getChildAt(i);
      if (child instanceof ArrangementCheckBoxNode<?> && data.equals(((ArrangementCheckBoxNode)child).getValue())) {
        return i;
      }
    }
    return -1;
  }

  @SuppressWarnings({"unchecked", "MethodMayBeStatic"})
  @NotNull
  public List<ArrangementGroupingRule> buildRules(@NotNull TreeNode groupsRoot) {
    List<ArrangementGroupingRule> result = new ArrayList<ArrangementGroupingRule>();
    for (int i = 0; i < groupsRoot.getChildCount(); i++) {
      ArrangementCheckBoxNode<ArrangementGroupingType> groupNode =
        (ArrangementCheckBoxNode<ArrangementGroupingType>)groupsRoot.getChildAt(i);
      if (!groupNode.isSelected()) {
        continue;
      }
      ArrangementGroupingType groupingType = groupNode.getValue();
      if (groupNode.getChildCount() < 1) {
        result.add(new ArrangementGroupingRule(groupingType));
        continue; 
      }

      ArrangementComboBoxNode<ArrangementEntryOrderType> orderNode =
        (ArrangementComboBoxNode<ArrangementEntryOrderType>)groupNode.getChildAt(0);
      ArrangementEntryOrderType orderType = orderNode.getSelectedValue();
      result.add(new ArrangementGroupingRule(groupingType, orderType));
    }
    return result;
  }
}
