// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.match.tokens;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Svetlana.Zemlyanskaya
 */
public final class ArrangementRuleAliasControl extends ArrangementMatchingRulesControl {
  public static final @NotNull DataKey<ArrangementRuleAliasControl> KEY = DataKey.create("Arrangement.Alias.Rule.Control");

  public ArrangementRuleAliasControl(@NotNull ArrangementStandardSettingsManager settingsManager,
                                     @NotNull ArrangementColorsProvider colorsProvider,
                                     @NotNull RepresentationCallback callback) {
    super(settingsManager, colorsProvider, callback);
  }

  public List<StdArrangementMatchRule> getRuleSequences() {
    final List<StdArrangementMatchRule> rulesSequences = new ArrayList<>();
    for (int i = 0; i < getModel().getSize(); i++) {
      Object element = getModel().getElementAt(i);
      if (element instanceof StdArrangementMatchRule) {
        rulesSequences.add((StdArrangementMatchRule)element);
      }
    }
    return rulesSequences;
  }

  public void setRuleSequences(Collection<? extends StdArrangementMatchRule> sequences) {
    myComponents.clear();
    getModel().clear();

    if (sequences == null) {
      return;
    }

    for (StdArrangementMatchRule rule : sequences) {
      getModel().add(rule);
    }
  }
}
