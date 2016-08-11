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
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 9/17/12 11:53 AM
 */
public class StdArrangementSettings implements ArrangementSettings {
  @NotNull private final   List<ArrangementSectionRule> mySectionRules     = new ArrayList<>();
  @NotNull private final   List<ArrangementGroupingRule> myGroupings       = new ArrayList<>();

  // cached values
  @NotNull protected final List<StdArrangementMatchRule> myRulesByPriority =
    Collections.synchronizedList(new ArrayList<StdArrangementMatchRule>());

  public StdArrangementSettings() {
  }

  @SuppressWarnings("unchecked")
  public StdArrangementSettings(@NotNull List<ArrangementSectionRule> rules) {
    this(Collections.EMPTY_LIST, rules);
  }

  public StdArrangementSettings(@NotNull List<ArrangementGroupingRule> groupingRules,
                                @NotNull List<ArrangementSectionRule> sectionRules) {
    myGroupings.addAll(groupingRules);
    mySectionRules.addAll(sectionRules);
  }

  public static StdArrangementSettings createByMatchRules(@NotNull List<ArrangementGroupingRule> groupingRules,
                                                          @NotNull List<StdArrangementMatchRule> matchRules) {
    final List<ArrangementSectionRule> sectionRules = new ArrayList<>();
    for (StdArrangementMatchRule rule : matchRules) {
      sectionRules.add(ArrangementSectionRule.create(rule));
    }
    return new StdArrangementSettings(groupingRules, sectionRules);
  }

  @NotNull
  protected List<ArrangementGroupingRule> cloneGroupings() {
    final ArrayList<ArrangementGroupingRule> groupings = new ArrayList<>();
    for (ArrangementGroupingRule grouping : myGroupings) {
      groupings.add(grouping.clone());
    }
    return groupings;
  }

  @NotNull
  protected List<ArrangementSectionRule> cloneSectionRules() {
    final ArrayList<ArrangementSectionRule> rules = new ArrayList<>();
    for (ArrangementSectionRule rule : mySectionRules) {
      rules.add(rule.clone());
    }
    return rules;
  }

  @NotNull
  @Override
  public ArrangementSettings clone() {
    return new StdArrangementSettings(cloneGroupings(), cloneSectionRules());
  }

  @Override
  @NotNull
  public List<ArrangementGroupingRule> getGroupings() {
    return myGroupings;
  }

  @NotNull
  @Override
  public List<ArrangementSectionRule> getSections() {
    return mySectionRules;
  }

  @NotNull
  @Override
  public List<StdArrangementMatchRule> getRules() {
    return ArrangementUtil.collectMatchRules(mySectionRules);
  }

  @NotNull
  @Override
  public List<? extends ArrangementMatchRule> getRulesSortedByPriority() {
    synchronized (myRulesByPriority) {
      if (myRulesByPriority.isEmpty()) {
        for (ArrangementSectionRule rule : mySectionRules) {
          myRulesByPriority.addAll(rule.getMatchRules());
        }
        ContainerUtil.sort(myRulesByPriority);
      }
    }
    return myRulesByPriority;
  }

  public void addRule(@NotNull StdArrangementMatchRule rule) {
    addSectionRule(rule);
    myRulesByPriority.clear();
  }

  public void addSectionRule(@NotNull StdArrangementMatchRule rule) {
    mySectionRules.add(ArrangementSectionRule.create(rule));
  }

  public void addGrouping(@NotNull ArrangementGroupingRule rule) {
    myGroupings.add(rule);
  }

  @Override
  public int hashCode() {
    int result = mySectionRules.hashCode();
    result = 31 * result + myGroupings.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StdArrangementSettings settings = (StdArrangementSettings)o;

    if (!myGroupings.equals(settings.myGroupings)) return false;
    if (!mySectionRules.equals(settings.mySectionRules)) return false;

    return true;
  }
}
