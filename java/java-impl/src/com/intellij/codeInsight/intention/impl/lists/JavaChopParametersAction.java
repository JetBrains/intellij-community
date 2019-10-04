// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class JavaChopParametersAction extends AbstractJavaChopListAction<PsiParameterList, PsiParameter> {
  @Nullable("When failed")
  @Override
  PsiParameterList extractList(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiParameterList.class, false);
  }

  @Nullable("When failed")
  @Override
  List<PsiParameter> getElements(@NotNull PsiParameterList list) {
    return Arrays.asList(list.getParameters());
  }


  @Override
  boolean needTailBreak(@NotNull PsiParameter last) {
    return CodeStyle.getLanguageSettings(last.getContainingFile(), JavaLanguage.INSTANCE).METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE;
  }

  @Override
  boolean needHeadBreak(@NotNull PsiParameter first) {
    return CodeStyle.getLanguageSettings(first.getContainingFile(), JavaLanguage.INSTANCE).METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Put parameters on separate lines";
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
