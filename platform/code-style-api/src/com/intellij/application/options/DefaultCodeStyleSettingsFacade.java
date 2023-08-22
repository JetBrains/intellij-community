// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsFacade;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultCodeStyleSettingsFacade implements CodeStyleSettingsFacade {
  private final @NotNull CodeStyleSettings mySettings;
  private @Nullable Language myLanguage;
  private @Nullable final FileType myFileType;

  public DefaultCodeStyleSettingsFacade(@NotNull CodeStyleSettings settings, @Nullable FileType fileType) {
    mySettings = settings;
    myFileType = fileType;
    myLanguage = fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : Language.ANY;
  }

  @Override
  public DefaultCodeStyleSettingsFacade withLanguage(@NotNull Language language) {
    myLanguage = language;
    return this;
  }

  @NotNull
  private CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    return myFileType != null ? mySettings.getIndentOptions(myFileType) : mySettings.getIndentOptions();
  }

  @NotNull
  private CommonCodeStyleSettings getCommonSettings() {
    return mySettings.getCommonSettings(myLanguage);
  }

  @Override
  public final int getTabSize() {
    return getIndentOptions().TAB_SIZE;
  }

  @Override
  public final int getIndentSize() {
    return getIndentOptions().INDENT_SIZE;
  }

  @Override
  public boolean isSpaceBeforeComma() {
    return getCommonSettings().SPACE_BEFORE_COMMA;
  }

  @Override
  public boolean isSpaceAfterComma() {
    return getCommonSettings().SPACE_AFTER_COMMA;
  }

  @Override
  public boolean isSpaceAroundAssignmentOperators() {
    return getCommonSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS;
  }
}
