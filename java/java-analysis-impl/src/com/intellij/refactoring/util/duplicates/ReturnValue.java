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

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ReturnValue {
  boolean isEquivalent(ReturnValue other);

  @Nullable
  PsiStatement createReplacement(@NotNull PsiMethod extractedMethod, @NotNull PsiMethodCallExpression methodCallExpression, @Nullable PsiType returnType) throws IncorrectOperationException;

  static boolean areEquivalent(@Nullable ReturnValue value1, @Nullable ReturnValue value2) {
    return value1 == null && value2 == null ||
           value1 != null && value1.isEquivalent(value2);
  }
}
