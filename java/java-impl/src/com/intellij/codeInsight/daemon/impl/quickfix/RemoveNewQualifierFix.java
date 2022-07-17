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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveNewQualifierFix implements IntentionAction {
  private final @NotNull PsiNewExpression expression;
  private final @Nullable PsiClass aClass;

  public RemoveNewQualifierFix(@NotNull PsiNewExpression expression, @Nullable PsiClass aClass) {
    this.expression = expression;
    this.aClass = aClass;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new RemoveNewQualifierFix(PsiTreeUtil.findSameElementInCopy(expression, target), aClass);
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("remove.qualifier.fix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.qualifier.fix");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
      expression.isValid() && (aClass == null || aClass.isValid()) && BaseIntentionAction.canModify(expression);
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return expression.getContainingFile();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    PsiExpression qualifier = expression.getQualifier();
    if (qualifier != null) {
      qualifier.delete();
    }
    if (aClass != null && classReference != null) {
      classReference.bindToElement(aClass);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
