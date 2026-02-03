// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementUiComponent;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokenUiRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DefaultArrangementUiComponentFactory implements ArrangementUiComponent.Factory {

  @Override
  public @Nullable ArrangementUiComponent build(@NotNull StdArrangementTokenUiRole role,
                                                @NotNull List<? extends ArrangementSettingsToken> tokens,
                                                @NotNull ArrangementColorsProvider colorsProvider,
                                                @NotNull ArrangementStandardSettingsManager settingsManager)
  {
    switch (role) {
      case CHECKBOX -> {
        if (tokens.size() != 1) {
          throw new IllegalArgumentException("Can't build a checkbox token for elements " + tokens);
        }
        else {
          return new ArrangementCheckBoxUiComponent(tokens.get(0));
        }
      }
      case COMBO_BOX -> {
        if (tokens.isEmpty()) {
          throw new IllegalArgumentException("Can't build a combo box token with empty content");
        }
        return new ArrangementComboBoxUiComponent(tokens);
      }
      case LABEL -> {
        if (tokens.size() != 1) {
          throw new IllegalArgumentException("Can't build a label token for elements " + tokens);
        }
        return new ArrangementLabelUiComponent(tokens.get(0));
      }
      case TEXT_FIELD -> {
        if (tokens.size() != 1) {
          throw new IllegalArgumentException("Can't build a text field token for elements " + tokens);
        }
        return new ArrangementTextFieldUiComponent(tokens.get(0));
      }
      case BULB -> {
        if (tokens.size() != 1) {
          throw new IllegalArgumentException("Can't build a bulb token for elements " + tokens);
        }
        return new ArrangementAtomMatchConditionComponent(
          settingsManager, colorsProvider, new ArrangementAtomMatchCondition(tokens.get(0)), null
        );
      }
    }
    return null;
  }
}
