// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiNameHelperImpl extends PsiNameHelper {
  private final LanguageLevelProjectExtension myLanguageLevelExtension;

  public PsiNameHelperImpl(Project project) {
    myLanguageLevelExtension = LanguageLevelProjectExtension.getInstance(project);
  }

  @Override
  public boolean isIdentifier(@Nullable String text) {
    return isIdentifier(text, getLanguageLevel());
  }

  protected @NotNull LanguageLevel getLanguageLevel() {
    return myLanguageLevelExtension.getLanguageLevel();
  }

  @Override
  public boolean isIdentifier(@Nullable String text, @NotNull LanguageLevel languageLevel) {
    return text != null && StringUtil.isJavaIdentifier(text) && !PsiUtil.isKeyword(text, languageLevel);
  }

  @Override
  public boolean isKeyword(@Nullable String text) {
    if (text == null) return false;
    @NotNull LanguageLevel level = getLanguageLevel();
    return PsiUtil.isKeyword(text, level);
  }

  @Override
  public boolean isQualifiedName(@Nullable String text) {
    if (text == null) return false;
    int index = 0;
    while (true) {
      int index1 = text.indexOf('.', index);
      if (index1 < 0) index1 = text.length();
      if (!isIdentifier(text.substring(index, index1))) return false;
      if (index1 == text.length()) return true;
      index = index1 + 1;
    }
  }

  public static PsiNameHelper getInstance() {
    return new PsiNameHelperImpl() {
      @Override
      protected @NotNull LanguageLevel getLanguageLevel() {
        return LanguageLevel.HIGHEST;
      }
    };
  }

  private PsiNameHelperImpl() {
    myLanguageLevelExtension = null;
  }
}
