// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddEmptyRecordHeaderFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public AddEmptyRecordHeaderFix(@NotNull PsiClass record) {
    super(record);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass record = (PsiClass)startElement;
    if (!record.isRecord() || record.getRecordHeader() != null) return;
    PsiTypeParameterList typeParameterList = record.getTypeParameterList();
    if (typeParameterList == null) return;
    PsiRecordHeader recordHeader = JavaPsiFacade.getElementFactory(project).createRecordHeaderFromText("", record);
    record.addAfter(recordHeader, typeParameterList);
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
}
