// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.match.tokens;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementRuleAliasToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Svetlana.Zemlyanskaya
 */
public final class ArrangementRuleAliasConfigurable implements UnnamedConfigurable {
  private final StdArrangementRuleAliasToken myToken;
  private final ArrangementRuleAliasesPanel myTokenRulesPanel;

  public ArrangementRuleAliasConfigurable(@NotNull ArrangementStandardSettingsManager settingsManager,
                                          @NotNull ArrangementColorsProvider colorsProvider,
                                          @NotNull StdArrangementRuleAliasToken token) {
    myToken = token;
    myTokenRulesPanel = new ArrangementRuleAliasesPanel(settingsManager, colorsProvider);
    myTokenRulesPanel.setRuleSequences(token.getDefinitionRules());
  }

  @Override
  public @Nullable JComponent createComponent() {
    return myTokenRulesPanel;
  }

  @Override
  public boolean isModified() {
    final List<StdArrangementMatchRule> newRules = myTokenRulesPanel.getRuleSequences();
    return !newRules.equals(myToken.getDefinitionRules());
  }

  @Override
  public void apply() throws ConfigurationException {
    myToken.setDefinitionRules(myTokenRulesPanel.getRuleSequences());
  }

  @Override
  public void reset() {
    myTokenRulesPanel.setRuleSequences(myToken.getDefinitionRules());
  }
}
