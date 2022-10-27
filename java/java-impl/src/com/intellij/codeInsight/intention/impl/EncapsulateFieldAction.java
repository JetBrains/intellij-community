// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.ide.DataManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
public class EncapsulateFieldAction extends BaseRefactoringIntentionAction {

  @NotNull
  @Override
  public String getText() {
    return JavaBundle.message("intention.encapsulate.field.text");
  }

  @NotNull
  @Override
  public final String getFamilyName() {
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
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiField field = getField(element);
    if (field == null) {
      return;
    }

    DataContext dataContext = DataManager.getInstance().getDataContext(editor.getComponent());
    JavaRefactoringActionHandlerFactory.getInstance().createEncapsulateFieldsHandler().invoke(project, new PsiElement[]{field}, dataContext);
  }


  @Nullable
  protected static PsiField getField(@Nullable PsiElement element) {
    if (!(element instanceof PsiIdentifier)) {
      return null;
    }

    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression ref = (PsiReferenceExpression)parent;
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