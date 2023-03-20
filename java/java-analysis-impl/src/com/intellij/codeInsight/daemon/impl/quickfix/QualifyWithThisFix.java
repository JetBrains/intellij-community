/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QualifyWithThisFix implements IntentionAction {
  private final PsiClass myContainingClass;
  private final PsiElement myExpression;

  public QualifyWithThisFix(@NotNull PsiClass containingClass, @NotNull PsiElement expression) {
    myContainingClass = containingClass;
    myExpression = expression;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new QualifyWithThisFix(myContainingClass, PsiTreeUtil.findSameElementInCopy(myExpression, target));
  }

  @NotNull
  @Override
  public String getText() {
    return JavaAnalysisBundle.message("qualify.with.0.this", myContainingClass.getName());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiThisExpression thisExpression =
      RefactoringChangeUtil.createThisExpression(PsiManager.getInstance(project), myContainingClass);
    ((PsiReferenceExpression)myExpression).setQualifierExpression(thisExpression);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
