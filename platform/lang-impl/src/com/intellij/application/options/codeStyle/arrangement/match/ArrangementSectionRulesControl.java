// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.match.tokens.ArrangementRuleAliasDialog;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementRuleAliasToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.application.options.codeStyle.arrangement.match.ArrangementSectionRuleManager.ArrangementSectionRuleData;

public final class ArrangementSectionRulesControl extends ArrangementMatchingRulesControl {
  public static final @NotNull DataKey<ArrangementSectionRulesControl> KEY = DataKey.create("Arrangement.Rule.Match.Control");
  private static final @NotNull Logger LOG = Logger.getInstance(ArrangementSectionRulesControl.class);
  private final @NotNull ArrangementColorsProvider myColorsProvider;
  private final @NotNull ArrangementStandardSettingsManager mySettingsManager;

  private final @Nullable ArrangementSectionRuleManager mySectionRuleManager;
  private @Nullable ArrangementStandardSettingsManager myExtendedSettingsManager;

  public ArrangementSectionRulesControl(@NotNull Language language,
                                        @NotNull ArrangementStandardSettingsManager settingsManager,
                                        @NotNull ArrangementColorsProvider colorsProvider,
                                        @NotNull RepresentationCallback callback) {
    super(settingsManager, colorsProvider, callback);
    mySectionRuleManager = ArrangementSectionRuleManager.getInstance(language, settingsManager, colorsProvider, this);
    mySettingsManager = settingsManager;
    myColorsProvider = colorsProvider;
  }

  private static void appendBufferedSectionRules(@NotNull List<? super ArrangementSectionRule> result,
                                                 @NotNull List<? extends StdArrangementMatchRule> buffer,
                                                 @Nullable String currentSectionStart) {
    if (currentSectionStart == null) {
      return;
    }

    if (buffer.isEmpty()) {
      result.add(ArrangementSectionRule.create(currentSectionStart, null));
    }
    else {
      result.add(ArrangementSectionRule.create(currentSectionStart, null, buffer.get(0)));
      for (int j = 1; j < buffer.size(); j++) {
        result.add(ArrangementSectionRule.create(buffer.get(j)));
      }
      buffer.clear();
    }
  }

  @Override
  protected MatchingRulesRendererBase createRender() {
    return new MatchingRulesRenderer();
  }

  @Override
  protected @NotNull ArrangementMatchingRulesValidator createValidator() {
    return new ArrangementSectionRulesValidator(getModel(), mySectionRuleManager);
  }

  public @Nullable ArrangementSectionRuleManager getSectionRuleManager() {
    return mySectionRuleManager;
  }

  public List<ArrangementSectionRule> getSections() {
    if (getModel().getSize() <= 0) {
      return Collections.emptyList();
    }

    final List<ArrangementSectionRule> result = new ArrayList<>();
    final List<StdArrangementMatchRule> buffer = new ArrayList<>();
    String currentSectionStart = null;
    for (int i = 0; i < getModel().getSize(); i++) {
      final Object element = getModel().getElementAt(i);
      if (element instanceof StdArrangementMatchRule) {
        final ArrangementSectionRuleData sectionRule =
          mySectionRuleManager == null ? null : mySectionRuleManager.getSectionRuleData((StdArrangementMatchRule)element);
        if (sectionRule != null) {
          if (sectionRule.isSectionStart()) {
            appendBufferedSectionRules(result, buffer, currentSectionStart);
            currentSectionStart = sectionRule.getText();
          }
          else {
            result.add(ArrangementSectionRule.create(StringUtil.notNullize(currentSectionStart), sectionRule.getText(), buffer));
            buffer.clear();
            currentSectionStart = null;
          }
        }
        else {
          if (currentSectionStart == null) {
            result.add(ArrangementSectionRule.create((StdArrangementMatchRule)element));
          }
          else {
            buffer.add((StdArrangementMatchRule)element);
          }
        }
      }
    }

    appendBufferedSectionRules(result, buffer, currentSectionStart);
    return result;
  }

  public void setSections(@Nullable List<? extends ArrangementSectionRule> sections) {
    final List<StdArrangementMatchRule> rules = sections == null ? null : ArrangementUtil.collectMatchRules(sections);
    myComponents.clear();
    getModel().clear();

    if (rules == null) {
      return;
    }

    for (StdArrangementMatchRule rule : rules) {
      getModel().add(rule);
    }

    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info("Arrangement matching rules list is refreshed. Given rules:");
      for (StdArrangementMatchRule rule : rules) {
        LOG.info("  " + rule.toString());
      }
    }
  }

  public @Nullable Collection<StdArrangementRuleAliasToken> getRulesAliases() {
    return myExtendedSettingsManager == null ? null : myExtendedSettingsManager.getRuleAliases();
  }

  public void setRulesAliases(@Nullable Collection<StdArrangementRuleAliasToken> aliases) {
    if (aliases != null) {
      myExtendedSettingsManager = new ArrangementStandardSettingsManager(mySettingsManager.getDelegate(), myColorsProvider, aliases);
      myEditor = new ArrangementMatchingRuleEditor(myExtendedSettingsManager, myColorsProvider, this);
    }
  }

  @Override
  public void showEditor(int rowToEdit) {
    if (mySectionRuleManager != null && mySectionRuleManager.isSectionRule(getModel().getElementAt(rowToEdit))) {
      mySectionRuleManager.showEditor(rowToEdit);
    }
    else {
      super.showEditor(rowToEdit);
    }
  }

  public @NotNull ArrangementRuleAliasDialog createRuleAliasEditDialog() {
    final Set<String> tokenIds = new HashSet<>();
    final List<ArrangementSectionRule> sections = getSections();
    for (ArrangementSectionRule section : sections) {
      for (StdArrangementMatchRule rule : section.getMatchRules()) {
        rule.getMatcher().getCondition().invite(new ArrangementMatchConditionVisitor() {
          @Override
          public void visit(@NotNull ArrangementAtomMatchCondition condition) {
            if (ArrangementUtil.isAliasedCondition(condition)) {
              tokenIds.add(condition.getType().getId());
            }
          }

          @Override
          public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
            for (ArrangementMatchCondition operand : condition.getOperands()) {
              operand.invite(this);
            }
          }
        });
      }
    }

    final Collection<StdArrangementRuleAliasToken> aliases = getRulesAliases();
    assert aliases != null;
    return new ArrangementRuleAliasDialog(null, mySettingsManager, myColorsProvider, aliases, tokenIds);
  }

  private final class MatchingRulesRenderer extends MatchingRulesRendererBase {
    @Override
    public boolean allowModifications(StdArrangementMatchRule rule) {
      return !(mySectionRuleManager != null && mySectionRuleManager.isSectionRule(rule));
    }
  }
}
