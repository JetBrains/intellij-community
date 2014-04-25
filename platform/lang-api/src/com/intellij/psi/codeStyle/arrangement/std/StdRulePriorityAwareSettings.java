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
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.RulePriorityAwareSettings;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated use {@link StdArrangementSettings} instead
 * @author Svetlana.Zemlyanskaya
 */
public class StdRulePriorityAwareSettings extends StdArrangementSettings implements RulePriorityAwareSettings {
  public StdRulePriorityAwareSettings(@NotNull List<StdArrangementMatchRule> rules) {
    super(wrapMatchRulesIntoSections(rules));
  }

  public StdRulePriorityAwareSettings(@NotNull List<ArrangementGroupingRule> groupingRules,
                                      @NotNull List<StdArrangementMatchRule> matchRules) {
    super(groupingRules, wrapMatchRulesIntoSections(matchRules));
  }

  public StdRulePriorityAwareSettings() {
    super();
  }

  @NotNull
  @Override
  public ArrangementSettings clone() {
    return new StdRulePriorityAwareSettings(cloneGroupings(), ArrangementUtil.collectMatchRules(cloneSectionRules()));
  }

  private static List<ArrangementSectionRule> wrapMatchRulesIntoSections(@NotNull List<StdArrangementMatchRule> matchRules) {
    final List<ArrangementSectionRule> sectionRules = new ArrayList<ArrangementSectionRule>();
    for (StdArrangementMatchRule rule : matchRules) {
      sectionRules.add(ArrangementSectionRule.create(rule));
    }
    return sectionRules;
  }
}
