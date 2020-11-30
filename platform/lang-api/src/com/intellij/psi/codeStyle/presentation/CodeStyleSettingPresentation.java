// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.presentation;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CodeStyleSettingPresentation {

  public static class SettingsGroup {
    @Nullable
    @NlsContexts.Label
    public final String name;

    public SettingsGroup(@Nullable @NlsContexts.Label String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof SettingsGroup) {
        SettingsGroup other = (SettingsGroup) o;
        return name != null && name.equals(other.name);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return name == null ? 0 : name.hashCode();
    }

    public boolean isNull() {
      return name == null;
    }
  }

  @NotNull
  protected String myFieldName;

  @NotNull
  @NlsContexts.Label
  protected String myUiName;

  public CodeStyleSettingPresentation(@NotNull String fieldName, @NotNull @NlsContexts.Label String uiName) {
    myFieldName = fieldName;
    myUiName = uiName;
  }

  @NotNull
  public String getFieldName() {
    return myFieldName;
  }

  @NotNull
  @NlsContexts.Label
  public String getUiName() {
    return myUiName;
  }

  public void setUiName(@NotNull @NlsContexts.Label String newName) {
    myUiName = newName;
  }

  public @NotNull @NlsSafe String getValueUiName(@NotNull Object value) {
    return value.toString();
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof CodeStyleSettingPresentation) && ((CodeStyleSettingPresentation)o).getFieldName().equals(getFieldName());
  }

  @Override
  public int hashCode() {
    return myFieldName.hashCode();
  }


  /**
   * Returns an immutable map containing all standard settings in a mapping of type (group -> settings contained in the group).
   * Notice that lists containing settings for a specific group are also immutable. Use copies to make modifications.
   * @param settingsType type to get standard settings for
   * @return mapping setting groups to contained setting presentations
   */
  @NotNull
  public static Map<SettingsGroup, List<CodeStyleSettingPresentation>> getStandardSettings(LanguageCodeStyleSettingsProvider.SettingsType settingsType) {
    switch (settingsType) {
      case BLANK_LINES_SETTINGS:
        return CodeStyleSettingsPresentations.getInstance().getBlankLinesStandardSettings();
      case SPACING_SETTINGS:
        return CodeStyleSettingsPresentations.getInstance().getSpacingStandardSettings();
      case WRAPPING_AND_BRACES_SETTINGS:
        return CodeStyleSettingsPresentations.getInstance().getWrappingAndBracesStandardSettings();
      case INDENT_SETTINGS:
        return CodeStyleSettingsPresentations.getInstance().getIndentStandardSettings();
      case LANGUAGE_SPECIFIC:
    }
    return new LinkedHashMap<>();
  }
}
