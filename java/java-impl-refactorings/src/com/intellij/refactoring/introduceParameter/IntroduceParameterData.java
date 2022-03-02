// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import it.unimi.dsi.fastutil.ints.IntList;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IntroduceParameterData {
  @NotNull
  Project getProject();

  PsiMethod getMethodToReplaceIn();

  @NotNull
  PsiMethod getMethodToSearchFor();

  ExpressionWrapper getParameterInitializer();

  @NotNull
  String getParameterName();

  @MagicConstant(intValues = {
    IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE})
  int getReplaceFieldsWithGetters();

  boolean isDeclareFinal();

  boolean isGenerateDelegate();

  @NotNull
  PsiType getForcedType();

  IntList getParameterListToRemove();

  interface ExpressionWrapper<RealExpression extends PsiElement> {
    @NotNull
    String getText();

    @Nullable
    PsiType getType();

    @NotNull
    RealExpression getExpression();

  }
}
