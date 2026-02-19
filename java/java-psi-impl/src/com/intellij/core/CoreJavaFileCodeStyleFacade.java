// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettingsFacade;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import org.jetbrains.annotations.NotNull;

public class CoreJavaFileCodeStyleFacade implements JavaFileCodeStyleFacade {
  @Override
  public int getNamesCountToUseImportOnDemand() {
    return 3;
  }

  @Override
  public boolean isToImportOnDemand(String qualifiedName) {
    return false;
  }

  @Override
  public boolean useFQClassNames() {
    return false;
  }

  @Override
  public boolean isJavaDocLeadingAsterisksEnabled() {
    return true;
  }

  @Override
  public boolean isGenerateFinalParameters() {
    return false;
  }

  @Override
  public boolean isGenerateFinalLocals() {
    return false;
  }

  @Override
  public CodeStyleSettingsFacade withLanguage(@NotNull Language language) {
    return this;
  }

  @Override
  public int getTabSize() {
    return 4;
  }

  @Override
  public int getIndentSize() {
    return 4;
  }

  @Override
  public boolean isSpaceBeforeComma() {
    return false;
  }

  @Override
  public boolean isSpaceAfterComma() {
    return true;
  }

  @Override
  public boolean isSpaceAroundAssignmentOperators() {
    return true;
  }
}
