/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.codeStyle.arrangement.ArrangementExtendableSettings;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class StdArrangementExtendableSettings extends StdArrangementSettings implements ArrangementExtendableSettings {
  @NotNull private final Set<StdArrangementRuleAliasToken> myRulesAliases = new THashSet<>();

  // cached values
  @NotNull private final List<ArrangementSectionRule> myExtendedSectionRules = Collections.synchronizedList(new ArrayList<ArrangementSectionRule>());

  public StdArrangementExtendableSettings() {
    super();
  }

  public StdArrangementExtendableSettings(@NotNull List<ArrangementGroupingRule> groupingRules,
                                          @NotNull List<ArrangementSectionRule> sectionRules,
                                          @NotNull Collection<StdArrangementRuleAliasToken> rulesAliases) {
    super(groupingRules, sectionRules);
    myRulesAliases.addAll(rulesAliases);
  }

  public static StdArrangementExtendableSettings createByMatchRules(@NotNull List<ArrangementGroupingRule> groupingRules,
                                                                    @NotNull List<StdArrangementMatchRule> matchRules,
                                                                    @NotNull Collection<StdArrangementRuleAliasToken> rulesAliases) {
    final List<ArrangementSectionRule> sectionRules = new ArrayList<>();
    for (StdArrangementMatchRule rule : matchRules) {
      sectionRules.add(ArrangementSectionRule.create(rule));
    }
    return new StdArrangementExtendableSettings(groupingRules, sectionRules, rulesAliases);
  }

  @Override
  public Set<StdArrangementRuleAliasToken> getRuleAliases() {
    return myRulesAliases;
  }

  private Set<StdArrangementRuleAliasToken> cloneTokenDefinitions() {
    final Set<StdArrangementRuleAliasToken> definitions = new THashSet<>();
    for (StdArrangementRuleAliasToken definition : myRulesAliases) {
      definitions.add(definition.clone());
    }
    return definitions;
  }

  @Override
  public List<ArrangementSectionRule> getExtendedSectionRules() {
    synchronized (myExtendedSectionRules) {
      if (myExtendedSectionRules.isEmpty()) {
        final Map<String, StdArrangementRuleAliasToken> tokenIdToDefinition = new THashMap<>(myRulesAliases.size());
        for (StdArrangementRuleAliasToken alias : myRulesAliases) {
          final String id = alias.getId();
          tokenIdToDefinition.put(id, alias);
        }

        final List<ArrangementSectionRule> sections = getSections();
        for (ArrangementSectionRule section : sections) {
          final List<StdArrangementMatchRule> extendedRules = new ArrayList<>();
          for (StdArrangementMatchRule rule : section.getMatchRules()) {
            appendExpandedRules(rule, extendedRules, tokenIdToDefinition);
          }
          myExtendedSectionRules.add(ArrangementSectionRule.create(section.getStartComment(), section.getEndComment(), extendedRules));
        }
      }
    }
    return myExtendedSectionRules;
  }

  public void appendExpandedRules(@NotNull final StdArrangementMatchRule rule,
                                  @NotNull final List<StdArrangementMatchRule> rules,
                                  @NotNull final Map<String, StdArrangementRuleAliasToken> tokenIdToDefinition) {
    final List<StdArrangementMatchRule> sequence = getRuleSequence(rule, tokenIdToDefinition);
    if (sequence == null || sequence.isEmpty()) {
      rules.add(rule);
      return;
    }

    final ArrangementCompositeMatchCondition ruleTemplate = removeAliasRuleToken(rule.getMatcher().getCondition());
    for (StdArrangementMatchRule matchRule : sequence) {
      final ArrangementCompositeMatchCondition extendedRule = ruleTemplate.clone();
      extendedRule.addOperand(matchRule.getMatcher().getCondition());
      rules.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(extendedRule)));
    }
  }

  @Nullable
  private List<StdArrangementMatchRule> getRuleSequence(@NotNull final StdArrangementMatchRule rule,
                                                        @NotNull final Map<String, StdArrangementRuleAliasToken> tokenIdToDefinition) {
    final List<StdArrangementMatchRule> seqRule = ContainerUtil.newSmartList();
    rule.getMatcher().getCondition().invite(new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition condition) {
        final StdArrangementRuleAliasToken token = tokenIdToDefinition.get(condition.getType().getId());
        if (token != null && !token.getDefinitionRules().isEmpty()) {
          seqRule.addAll(token.getDefinitionRules());
        }
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
        for (ArrangementMatchCondition operand : condition.getOperands()) {
          if (!seqRule.isEmpty()) {
            return;
          }
          operand.invite(this);
        }
      }
    });
    return seqRule;
  }

  @NotNull
  private static ArrangementCompositeMatchCondition removeAliasRuleToken(final ArrangementMatchCondition original) {
    final ArrangementCompositeMatchCondition composite = new ArrangementCompositeMatchCondition();
    original.invite(new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition condition) {
        if (!ArrangementUtil.isAliasedCondition(condition)) {
          composite.addOperand(condition);
        }
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
        for (ArrangementMatchCondition c : condition.getOperands()) {
          c.invite(this);
        }
      }
    });
    return composite;
  }

  @Override
  public void addRule(@NotNull StdArrangementMatchRule rule) {
    addSectionRule(rule);
    myRulesByPriority.clear();
    myExtendedSectionRules.clear();
  }

  @NotNull
  @Override
  public List<? extends ArrangementMatchRule> getRulesSortedByPriority() {
    synchronized (myExtendedSectionRules) {
      if (myRulesByPriority.isEmpty()) {
        for (ArrangementSectionRule rule : getExtendedSectionRules()) {
          myRulesByPriority.addAll(rule.getMatchRules());
        }
        ContainerUtil.sort(myRulesByPriority);
      }
    }
    return myRulesByPriority;
  }

  @NotNull
  @Override
  public StdArrangementExtendableSettings clone() {
    return new StdArrangementExtendableSettings(cloneGroupings(), cloneSectionRules(), cloneTokenDefinitions());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StdArrangementExtendableSettings settings = (StdArrangementExtendableSettings)o;

    if (!super.equals(settings)) return false;
    if (!myRulesAliases.equals(settings.myRulesAliases)) return false;

    return true;
  }
}
