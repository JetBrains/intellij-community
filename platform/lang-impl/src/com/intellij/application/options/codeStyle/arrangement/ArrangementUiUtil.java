// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementUiComponent;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokenUiRole;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ArrangementUiUtil {
  private ArrangementUiUtil() {
  }

  public static @NotNull ArrangementUiComponent buildUiComponent(@NotNull StdArrangementTokenUiRole role,
                                                                 @NotNull List<? extends ArrangementSettingsToken> tokens,
                                                                 @NotNull ArrangementColorsProvider colorsProvider,
                                                                 @NotNull ArrangementStandardSettingsManager settingsManager)
    throws IllegalArgumentException
  {
    for (ArrangementUiComponent.Factory factory : ArrangementUiComponent.Factory.EP_NAME.getExtensionList()) {
      ArrangementUiComponent result = factory.build(role, tokens, colorsProvider, settingsManager);
      if (result != null) {
        return result;
      }
    }
    throw new IllegalArgumentException("Unsupported UI token role " + role);
  }
}
