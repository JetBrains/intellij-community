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

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class JavaSliceNullnessAnalyzer extends SliceNullnessAnalyzerBase {
  public JavaSliceNullnessAnalyzer() {
    super(JavaSlicerAnalysisUtil.LEAF_ELEMENT_EQUALITY, JavaSliceProvider.getInstance());
  }

  @NotNull
  @Override
  protected Nullability checkNullability(PsiElement element) {
    // null
    PsiElement value = element;
    if (value instanceof PsiExpression) {
      value = PsiUtil.deparenthesizeExpression((PsiExpression)value);
    }
    if (value instanceof PsiLiteralExpression) {
      return ((PsiLiteralExpression)value).getValue() == null ? Nullability.NULLABLE : Nullability.NOT_NULL;
    }

    // not null
    if (value instanceof PsiNewExpression) return Nullability.NOT_NULL;
    if (value instanceof PsiThisExpression) return Nullability.NOT_NULL;
    if (value instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)value).resolveMethod();
      if (method != null) {
        return NullableNotNullManager.getNullability(method);
      }
    }
    if (value instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)value).getOperationTokenType() == JavaTokenType.PLUS) {
      return Nullability.NOT_NULL; // "xxx" + var
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
      return Nullability.NOT_NULL;
    }

    if (value instanceof PsiLocalVariable || value instanceof PsiParameter) {
      Nullability result = DfaUtil.checkNullability((PsiVariable)value, context);
      if (result != Nullability.UNKNOWN) {
        return result;
      }
    }

    if (value instanceof PsiEnumConstant) return Nullability.NOT_NULL;

    if (value instanceof PsiModifierListOwner) {
      return NullableNotNullManager.getNullability((PsiModifierListOwner)value);
    }

    return Nullability.UNKNOWN;
  }
}
