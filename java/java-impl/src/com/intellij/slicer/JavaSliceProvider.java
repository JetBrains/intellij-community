/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class JavaSliceProvider implements SliceLanguageSupportProvider, SliceUsageTransformer {
  public static JavaSliceProvider getInstance() {
    return (JavaSliceProvider)LanguageSlicing.INSTANCE.forLanguage(JavaLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public SliceUsage createRootUsage(@NotNull PsiElement element, @NotNull SliceAnalysisParams params) {
    return JavaSliceUsage.createRootUsage(element, params);
  }

  @Nullable
  @Override
  public Collection<SliceUsage> transform(@NotNull SliceUsage usage) {
    if (usage instanceof JavaSliceUsage) return null;

    PsiElement element = usage.getElement();
    SliceUsage parent = usage.getParent();

    if (usage.params.dataFlowToThis && element instanceof PsiMethod) {
      return SliceUtil.collectMethodReturnValues(
        parent,
        parent instanceof JavaSliceUsage ? ((JavaSliceUsage)parent).getSubstitutor() : PsiSubstitutor.EMPTY,
        (PsiMethod) element
      );
    }

    if (!(element instanceof PsiExpression || element instanceof PsiVariable)) return null;
    SliceUsage newUsage = parent != null
                        ? new JavaSliceUsage(element, parent, PsiSubstitutor.EMPTY, 0, "")
                        : createRootUsage(element, usage.params);
    return Collections.singletonList(newUsage);
  }

  @Nullable
  @Override
  public PsiElement getExpressionAtCaret(PsiElement atCaret, boolean dataFlowToThis) {
    PsiElement element = PsiTreeUtil.getParentOfType(atCaret, PsiExpression.class, PsiVariable.class);
    if (dataFlowToThis && element instanceof PsiLiteralExpression) return null;
    return element;
  }

  @NotNull
  @Override
  public PsiElement getElementForDescription(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      PsiElement elementToSlice = ((PsiReferenceExpression)element).resolve();
      if (elementToSlice != null) {
        return elementToSlice;
      }
    }
    return element;
  }

  @NotNull
  @Override
  public SliceUsageCellRendererBase getRenderer() {
    return new SliceUsageCellRenderer();
  }

  @Override
  public void startAnalyzeLeafValues(@NotNull AbstractTreeStructure structure, @NotNull Runnable finalRunnable) {
    JavaSlicerAnalysisUtil.createLeafAnalyzer().startAnalyzeValues(structure, finalRunnable);
  }

  @Override
  public void startAnalyzeNullness(@NotNull AbstractTreeStructure structure, @NotNull Runnable finalRunnable) {
    new JavaSliceNullnessAnalyzer().startAnalyzeNullness(structure, finalRunnable);
  }

  @Override
  public void registerExtraPanelActions(@NotNull DefaultActionGroup actionGroup, @NotNull SliceTreeBuilder sliceTreeBuilder) {
    if (sliceTreeBuilder.dataFlowToThis) {
      actionGroup.add(new GroupByLeavesAction(sliceTreeBuilder));
      actionGroup.add(new CanItBeNullAction(sliceTreeBuilder));
    }
  }
}
