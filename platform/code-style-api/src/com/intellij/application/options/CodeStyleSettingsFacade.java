// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleSettingsFacade {
  private final @NotNull CodeStyleSettings mySettings;
  private @Nullable Language myLanguage;
  private @Nullable final FileType myFileType;

  public CodeStyleSettingsFacade(@NotNull CodeStyleSettings settings, @Nullable FileType fileType) {
    mySettings = settings;
    myFileType = fileType;
    myLanguage = fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : Language.ANY;
  }

  public CodeStyleSettingsFacade withLanguage(@NotNull Language language) {
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

  public final int getTabSize() {
    return getIndentOptions().TAB_SIZE;
  }

  public boolean isSpaceBeforeComma() {
    return getCommonSettings().SPACE_BEFORE_COMMA;
  }

  public boolean isSpaceAfterComma() {
    return getCommonSettings().SPACE_AFTER_COMMA;
  }

  public boolean isSpaceAroundAssignmentOperators() {
    return getCommonSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS;
  }
}
