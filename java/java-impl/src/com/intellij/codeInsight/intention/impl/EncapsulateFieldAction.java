// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.DataManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.PreviewableRefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EncapsulateFieldAction extends BaseRefactoringIntentionAction {
  @Override
  public @NotNull String getText() {
    return JavaBundle.message("intention.encapsulate.field.text");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof SyntheticElement){
      return false;
    }

    final PsiField field = getField(element);
    return field != null && !field.hasModifierProperty(PsiModifier.FINAL) && !field.hasModifierProperty(PsiModifier.PRIVATE);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiElement element = getElement(editor, psiFile);
    final PsiField field = getField(element);
    if (field == null) return IntentionPreviewInfo.EMPTY;
    RefactoringActionHandler handler = JavaRefactoringActionHandlerFactory.getInstance().createEncapsulateFieldsHandler();
    if (handler instanceof PreviewableRefactoringActionHandler previewableRefactoringActionHandler) {
      return previewableRefactoringActionHandler.generatePreview(project, field);
    }
    return IntentionPreviewInfo.EMPTY;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiField field = getField(element);
    if (field == null) return;
    DataContext dataContext = DataManager.getInstance().getDataContext(editor.getComponent());
    JavaRefactoringActionHandlerFactory.getInstance().createEncapsulateFieldsHandler().invoke(project, new PsiElement[]{field}, dataContext);
  }

  private static @Nullable PsiField getField(@Nullable PsiElement element) {
    if (!(element instanceof PsiIdentifier)) {
      return null;
    }

    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceExpression ref)) {
      return null;
    }
    final PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier == null || qualifier instanceof PsiThisExpression) {
      return null;
    }

    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiField)) {
      return null;
    }
    return (PsiField)resolved;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}