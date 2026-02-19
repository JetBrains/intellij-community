// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    public final @Nullable @NlsContexts.Label String name;

    public SettingsGroup(@Nullable @NlsContexts.Label String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof SettingsGroup other) {
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

  protected @NotNull String myFieldName;

  protected @NotNull @NlsContexts.Label String myUiName;

  public CodeStyleSettingPresentation(@NotNull String fieldName, @NotNull @NlsContexts.Label String uiName) {
    myFieldName = fieldName;
    myUiName = uiName;
  }

  public @NotNull String getFieldName() {
    return myFieldName;
  }

  public @NotNull @NlsContexts.Label String getUiName() {
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
  public static @NotNull Map<SettingsGroup, List<CodeStyleSettingPresentation>> getStandardSettings(LanguageCodeStyleSettingsProvider.SettingsType settingsType) {
    return switch (settingsType) {
      case BLANK_LINES_SETTINGS -> CodeStyleSettingsPresentations.getInstance().getBlankLinesStandardSettings();
      case SPACING_SETTINGS -> CodeStyleSettingsPresentations.getInstance().getSpacingStandardSettings();
      case WRAPPING_AND_BRACES_SETTINGS -> CodeStyleSettingsPresentations.getInstance().getWrappingAndBracesStandardSettings();
      case INDENT_SETTINGS -> CodeStyleSettingsPresentations.getInstance().getIndentStandardSettings();
      default -> new LinkedHashMap<>();
    };
  }
}
