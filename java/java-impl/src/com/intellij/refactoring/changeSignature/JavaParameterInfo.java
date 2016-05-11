/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public interface JavaParameterInfo extends ParameterInfo {
  @Nullable
  PsiType createType(@Nullable PsiElement context, final PsiManager manager) throws IncorrectOperationException;

  @Nullable
  default PsiType createType(@NotNull PsiElement context) {
    return createType(context, context.getManager());
  }

  String getTypeText();

  CanonicalTypes.Type getTypeWrapper();

  PsiExpression getValue(PsiCallExpression callExpression);

  @Nullable
  default PsiElement getActualValue(PsiElement callExpression, Object substitutor) {
    return callExpression instanceof PsiCallExpression ? getValue((PsiCallExpression)callExpression) : null;
  }

  boolean isVarargType();

  default void setType(@Nullable PsiType type) {}
}
