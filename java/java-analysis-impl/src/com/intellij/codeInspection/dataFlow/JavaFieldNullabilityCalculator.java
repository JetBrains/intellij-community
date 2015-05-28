/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaFieldNullabilityCalculator extends FieldNullabilityCalculator {

  @Nullable
  @Override
  public Nullness calculate(@NotNull PsiField field) {
    final List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
    if (initializers.isEmpty()) {
      return null;
    }

    boolean hasUnknowns = false;
    for (PsiExpression expression : initializers) {
      if (!(expression instanceof PsiReferenceExpression)) {
        hasUnknowns = true;
        continue;
      }
      PsiElement target = ((PsiReferenceExpression)expression).resolve();
      if (!(target instanceof PsiParameter)) {
        hasUnknowns = true;
        continue;
      }
      if (NullableNotNullManager.isNullable((PsiParameter)target)) {
        return Nullness.NULLABLE;
      }
      if (!NullableNotNullManager.isNotNull((PsiParameter)target)) {
        hasUnknowns = true;
      }
    }

    if (!hasUnknowns) {
      return Nullness.NOT_NULL;
    }
    else {
      if (DfaPsiUtil.isInitializedNotNull(field)) {
        return Nullness.NOT_NULL;
      }
    }
    
    return null;
  }
}
