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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsHandler;
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
    return CodeInsightBundle.message("intention.encapsulate.field.text");
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

    new EncapsulateFieldsHandler().invoke(project, new PsiElement[]{field}, null);
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