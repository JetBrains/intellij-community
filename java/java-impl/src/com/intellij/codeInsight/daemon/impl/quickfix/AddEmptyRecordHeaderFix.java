// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddEmptyRecordHeaderFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final SmartPsiElementPointer<PsiClass> myRecordPointer;

  public AddEmptyRecordHeaderFix(@NotNull PsiClass record) {
    super(record);
    this.myRecordPointer = SmartPointerManager.createPointer(record);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass record = myRecordPointer.dereference();
    if (record == null || !record.isRecord() || record.getRecordHeader() != null) return;
    PsiTypeParameterList typeParameterList = record.getTypeParameterList();
    if (typeParameterList == null) return;
    record.addAfter(createEmptyRecordHeader(project), typeParameterList);
  }

  private static PsiRecordHeader createEmptyRecordHeader(Project project) {
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, "record __DUMMY(){}");
    return file.getClasses()[0].getRecordHeader();
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
    return QuickFixBundle.message("add.empty.record.header");
  }
}
