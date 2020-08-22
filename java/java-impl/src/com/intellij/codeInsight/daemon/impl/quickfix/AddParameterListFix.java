// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddParameterListFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public AddParameterListFix(@NotNull PsiMethod method) {
    super(method);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!(startElement instanceof PsiMethod)) return;
    PsiMethod method = (PsiMethod)startElement;
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return;
    method.addAfter(JavaPsiFacade.getElementFactory(project).createParameterList(ArrayUtil.EMPTY_STRING_ARRAY, PsiType.EMPTY_ARRAY),
                identifier);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("insert.empty.parenthesis");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return super.isAvailable(project, file, editor, startElement, endElement) &&
           startElement instanceof PsiMethod && JavaPsiRecordUtil.isCompactConstructor((PsiMethod)startElement);
  }
}
