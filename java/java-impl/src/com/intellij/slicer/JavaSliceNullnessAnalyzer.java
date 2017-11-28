/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class JavaSliceNullnessAnalyzer extends SliceNullnessAnalyzerBase {
  public JavaSliceNullnessAnalyzer() {
    super(JavaSlicerAnalysisUtil.LEAF_ELEMENT_EQUALITY, JavaSliceProvider.getInstance());
  }

  @NotNull
  @Override
  protected Nullness checkNullness(PsiElement element) {
    // null
    PsiElement value = element;
    if (value instanceof PsiExpression) {
      value = PsiUtil.deparenthesizeExpression((PsiExpression)value);
    }
    if (value instanceof PsiLiteralExpression) {
      return ((PsiLiteralExpression)value).getValue() == null ? Nullness.NULLABLE : Nullness.NOT_NULL;
    }

    // not null
    if (value instanceof PsiNewExpression) return Nullness.NOT_NULL;
    if (value instanceof PsiThisExpression) return Nullness.NOT_NULL;
    if (value instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)value).resolveMethod();
      if (method != null && NullableNotNullManager.isNotNull(method)) return Nullness.NOT_NULL;
      if (method != null && NullableNotNullManager.isNullable(method)) return Nullness.NULLABLE;
    }
    if (value instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)value).getOperationTokenType() == JavaTokenType.PLUS) {
      return Nullness.NOT_NULL; // "xxx" + var
    }

    // unfortunately have to resolve here, since there can be no subnodes
    PsiElement context = value;
    if (value instanceof PsiReference) {
      PsiElement resolved = ((PsiReference)value).resolve();
      if (resolved instanceof PsiCompiledElement) {
        resolved = resolved.getNavigationElement();
      }
      value = resolved;
    }
    if (value instanceof PsiParameter && ((PsiParameter)value).getDeclarationScope() instanceof PsiCatchSection) {
      // exception thrown is always not null
      return Nullness.NOT_NULL;
    }

    if (value instanceof PsiLocalVariable || value instanceof PsiParameter) {
      Nullness result = DfaUtil.checkNullness((PsiVariable)value, context);
      if (result != Nullness.UNKNOWN) {
        return result;
      }
    }

    if (value instanceof PsiModifierListOwner) {
      if (NullableNotNullManager.isNotNull((PsiModifierListOwner)value)) return Nullness.NOT_NULL;
      if (NullableNotNullManager.isNullable((PsiModifierListOwner)value)) return Nullness.NULLABLE;
    }

    if (value instanceof PsiEnumConstant) return Nullness.NOT_NULL;
    return Nullness.UNKNOWN;
  }
}
