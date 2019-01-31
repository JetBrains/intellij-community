/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GotoReturnValue implements ReturnValue {
  @Override
  @Nullable
  public PsiStatement createReplacement(@NotNull final PsiMethod extractedMethod, @NotNull final PsiMethodCallExpression methodCallExpression, @Nullable PsiType returnType) throws IncorrectOperationException {
    if (!TypeConversionUtil.isBooleanType(extractedMethod.getReturnType())) return null;
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(methodCallExpression.getProject());
    final PsiIfStatement statement = (PsiIfStatement)elementFactory.createStatementFromText(getGotoStatement(), null);
    final PsiExpression condition = statement.getCondition();
    assert condition != null;
    condition.replace(methodCallExpression);
    return (PsiStatement)CodeStyleManager.getInstance(statement.getManager().getProject()).reformat(statement);
  }

  @NonNls
  public abstract String getGotoStatement();
}