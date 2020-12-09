// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.presentation.CodeStyleSettingPresentation;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class CodeStyleSpacesPanel extends OptionTreeWithPreviewPanel {
  public CodeStyleSpacesPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  public LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.SPACING_SETTINGS;
  }

  @Override
  protected void initTables() {
    Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> settingsMap = CodeStyleSettingPresentation
      .getStandardSettings(getSettingsType());

    for (Map.Entry<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> entry: settingsMap.entrySet()) {
      String groupName = entry.getKey().name;
      for (CodeStyleSettingPresentation setting: entry.getValue()) {
        initBooleanField(setting.getFieldName(), setting.getUiName(), groupName);
      }
    }
    for (@Nls String customOptionsGroup : myCustomOptions.keySet()) {
      initCustomOptions(customOptionsGroup);
    }
  }

  @Override
  protected @TabTitle @NotNull String getTabTitle() {
    return ApplicationBundle.message("title.spaces");
  }
}
