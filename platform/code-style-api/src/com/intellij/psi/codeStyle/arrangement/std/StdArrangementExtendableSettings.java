// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Svetlana.Zemlyanskaya
 */
public final class StdArrangementExtendableSettings extends StdArrangementSettings implements ArrangementExtendableSettings {
  @NotNull private final Set<StdArrangementRuleAliasToken> myRulesAliases = new HashSet<>();

  // cached values
  @NotNull private final List<ArrangementSectionRule> myExtendedSectionRules = Collections.synchronizedList(new ArrayList<>());

  public StdArrangementExtendableSettings() {
    super();
  }

  public StdArrangementExtendableSettings(@NotNull List<? extends ArrangementGroupingRule> groupingRules,
                                          @NotNull List<ArrangementSectionRule> sectionRules,
                                          @NotNull Collection<? extends StdArrangementRuleAliasToken> rulesAliases) {
    super(groupingRules, sectionRules);
    myRulesAliases.addAll(rulesAliases);
  }

  public static StdArrangementExtendableSettings createByMatchRules(@NotNull List<? extends ArrangementGroupingRule> groupingRules,
                                                                    @NotNull List<? extends StdArrangementMatchRule> matchRules,
                                                                    @NotNull Collection<? extends StdArrangementRuleAliasToken> rulesAliases) {
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
    final Set<StdArrangementRuleAliasToken> definitions = new HashSet<>();
    for (StdArrangementRuleAliasToken definition : myRulesAliases) {
      definitions.add(definition.clone());
    }
    return definitions;
  }

  @Override
  public List<ArrangementSectionRule> getExtendedSectionRules() {
    synchronized (myExtendedSectionRules) {
      if (myExtendedSectionRules.isEmpty()) {
        final Map<String, StdArrangementRuleAliasToken> tokenIdToDefinition = new HashMap<>(myRulesAliases.size());
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
                                  @NotNull final List<? super StdArrangementMatchRule> rules,
                                  @NotNull final Map<String, StdArrangementRuleAliasToken> tokenIdToDefinition) {
    final List<StdArrangementMatchRule> sequence = getRuleSequence(rule, tokenIdToDefinition);
    if (sequence.isEmpty()) {
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

  @NotNull
  private List<StdArrangementMatchRule> getRuleSequence(@NotNull final StdArrangementMatchRule rule,
                                                        @NotNull final Map<String, StdArrangementRuleAliasToken> tokenIdToDefinition) {
    final List<StdArrangementMatchRule> seqRule = new SmartList<>();
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
