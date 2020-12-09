// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import gnu.trove.TIntArrayList;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.refactoring.IntroduceParameterRefactoring.*;

public interface IntroduceParameterData {
  @NotNull
  Project getProject();

  PsiMethod getMethodToReplaceIn();

  @NotNull
  PsiMethod getMethodToSearchFor();

  ExpressionWrapper getParameterInitializer();

  @NotNull
  String getParameterName();

  @MagicConstant(intValues = {REPLACE_FIELDS_WITH_GETTERS_ALL, REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, REPLACE_FIELDS_WITH_GETTERS_NONE})
  int getReplaceFieldsWithGetters();

  boolean isDeclareFinal();

  boolean isGenerateDelegate();

  @NotNull
  PsiType getForcedType();

  @NotNull
  TIntArrayList getParametersToRemove();

  interface ExpressionWrapper<RealExpression extends PsiElement> {
    @NotNull
    String getText();

    @Nullable
    PsiType getType();

    @NotNull
    RealExpression getExpression();

  }
}
