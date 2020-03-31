// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.application.options.CodeStyle;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInsight.intention.impl.lists.JavaListUtils.getCallArgumentsList;

public class JavaChopArgumentsAction extends AbstractJavaChopListAction<PsiExpressionList, PsiExpression> {
  @Nullable("When failed")
  @Override
  PsiExpressionList extractList(@NotNull PsiElement element) {
    return getCallArgumentsList(element);
  }

  @Nullable("When failed")
  @Override
  List<PsiExpression> getElements(@NotNull PsiExpressionList list) {
    return Arrays.asList(list.getExpressions());
  }


  @Override
  boolean needTailBreak(@NotNull PsiExpression last) {
    return CodeStyle.getLanguageSettings(last.getContainingFile(), JavaLanguage.INSTANCE).CALL_PARAMETERS_RPAREN_ON_NEXT_LINE;
  }

  @Override
  boolean needHeadBreak(@NotNull PsiExpression first) {
    return CodeStyle.getLanguageSettings(first.getContainingFile(), JavaLanguage.INSTANCE).CALL_PARAMETERS_RPAREN_ON_NEXT_LINE;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.put.arguments.on.separate.lines");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
