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
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class JavaSliceNullnessAnalyzer extends SliceNullnessAnalyzerBase {
  public JavaSliceNullnessAnalyzer() {
    super(JavaSlicerAnalysisUtil.LEAF_ELEMENT_EQUALITY, JavaSliceProvider.getInstance());
  }

  @NotNull
  @Override
  protected Nullability checkNullability(PsiElement element) {
    if (element instanceof PsiExpression) {
      return NullabilityUtil.getExpressionNullability((PsiExpression)element, true);
    }
    return Nullability.UNKNOWN;
  }
}
