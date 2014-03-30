/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.RulePriorityAwareSettings;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class StdRulePriorityAwareSettings extends StdArrangementSettings implements RulePriorityAwareSettings {
  @NotNull private final List<StdArrangementMatchRule> myRulesByPriority = new ArrayList<StdArrangementMatchRule>();

  public StdRulePriorityAwareSettings(@NotNull List<StdArrangementMatchRule> rules) {
    super(rules);
  }

  public StdRulePriorityAwareSettings(@NotNull List<ArrangementGroupingRule> groupingRules,
                                      @NotNull List<StdArrangementMatchRule> matchRules) {
    super(groupingRules, matchRules);
  }

  public StdRulePriorityAwareSettings() {
    super();
  }

  @Override
  public void addRule(@NotNull StdArrangementMatchRule rule) {
    super.addRule(rule);
    myRulesByPriority.clear();
  }

  @NotNull
  @Override
  public List<? extends ArrangementMatchRule> getRulesSortedByPriority() {
    if (myRulesByPriority.isEmpty()) {
      myRulesByPriority.addAll(myRules);
      ContainerUtil.sort(myRulesByPriority);
    }
    return myRulesByPriority;
  }

  @NotNull
  @Override
  public ArrangementSettings clone() {
    return new StdRulePriorityAwareSettings(cloneGroupings(), cloneMatchRules());
  }
}
