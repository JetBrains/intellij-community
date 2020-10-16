// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.fields.valueEditors.CommaSeparatedIntegersValueEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions.getInstance;

final class MarginOptionsUtil {
  public static @NlsContexts.Label String getDefaultRightMarginText(@NotNull CodeStyleSettings settings) {
    return getDefaultValueText(Integer.toString(settings.getDefaultRightMargin()));
  }

  static @NlsContexts.Label String getDefaultVisualGuidesText(@NotNull CodeStyleSettings settings) {
    List<Integer> softMargins = settings.getDefaultSoftMargins();
    return getDefaultValueText(
      (softMargins.size() > 0
       ? CommaSeparatedIntegersValueEditor.intListToString(settings.getDefaultSoftMargins())
       : ApplicationBundle.message("settings.soft.margins.empty.list")));
  }

  static @NlsContexts.Label String getDefaultWrapOnTypingText(@NotNull CodeStyleSettings settings) {
    return getDefaultValueText(settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN ? "Yes" : "No");
  }

  static void customizeWrapOnTypingCombo(@NotNull JComboBox<String> wrapOnTypingCombo, @NotNull CodeStyleSettings settings) {
    wrapOnTypingCombo.setRenderer(SimpleListCellRenderer.create("", (@NlsSafe var value) -> {
      for (int i = 0; i < CodeStyleSettingsCustomizable.WRAP_ON_TYPING_VALUES.length; i++) {
        if (CodeStyleSettingsCustomizable.WRAP_ON_TYPING_VALUES[i] == CommonCodeStyleSettings.WrapOnTyping.DEFAULT.intValue) {
          if (getInstance().WRAP_ON_TYPING_OPTIONS[i].equals(value)) {
            return getDefaultWrapOnTypingText(settings);
          }
        }
      }
      return value;
    }));
  }

  static @NlsContexts.Label String getDefaultValueText(@NotNull String value) {
    return ApplicationBundle.message("settings.default.value.prefix", value);
  }
}
