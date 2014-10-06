/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class JavaSliceProvider extends SliceProvider {
  @Override
  public boolean processUsagesFlownDownTo(@NotNull PsiElement element,
                                          @NotNull Processor<SliceUsage> processor,
                                          @NotNull SliceUsage parent) {
    return SliceUtil.processUsagesFlownDownTo(element, processor, parent);
  }

  @Override
  public boolean processUsagesFlownFromThe(@NotNull PsiElement element,
                                           @NotNull Processor<SliceUsage> processor,
                                           @NotNull SliceUsage parent) {
    return SliceForwardUtil.processUsagesFlownFromThe(element, processor, parent);
  }

  @NotNull
  @Override
  public SliceUsageCellRenderer createSliceUsageCellRenderer() {
    return new JavaSliceUsageCellRenderer();
  }

  @Nullable
  @Override
  public PsiElement getExpressionAtCaret(@NotNull Editor editor, @NotNull PsiFile file, boolean isDataFlowToThis) {
    int offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    if (offset == 0) {
      return null;
    }
    PsiElement atCaret = file.findElementAt(offset);

    PsiElement element = PsiTreeUtil.getParentOfType(atCaret, PsiExpression.class, PsiVariable.class);
    if (isDataFlowToThis && element instanceof PsiLiteralExpression) return null;
    return element;
  }

  @NotNull
  @Override
  public PsiElement getElementToSlice(@NotNull PsiElement element) {
    PsiElement elementToSlice = element;
    if (element instanceof PsiReferenceExpression) elementToSlice = ((PsiReferenceExpression)element).resolve();
    if (elementToSlice == null) elementToSlice = element;
    return elementToSlice;
  }
}
