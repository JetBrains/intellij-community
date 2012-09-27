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
import com.intellij.application.options.codeStyle.arrangement.node.ArrangementSectionNode;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.*;
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
    for (ArrangementGroupingType groupingType : ArrangementGroupingType.values()) {
      if (!settingsFilter.isEnabled(groupingType, null)) {
        continue;
      }
      ArrangementCheckBoxNode groupingCheckNode = new ArrangementCheckBoxNode(displayManager.getDisplayValue(groupingType));
      result.add(groupingCheckNode);
    }
    return result;
  }

  public void applyRules(@NotNull List<ArrangementGroupingRule> rules, @NotNull TreeNode groupsRoot) {
    // TODO den implement
  }

  @NotNull
  public List<ArrangementGroupingRule> buildRules(@NotNull TreeNode groupsRoot) {
    List<ArrangementGroupingRule> result = new ArrayList<ArrangementGroupingRule>();
    // TODO den implement
    return result;
  }
}
