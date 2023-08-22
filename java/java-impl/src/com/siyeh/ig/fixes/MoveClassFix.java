/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class MoveClassFix extends RefactoringInspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("move.class.quickfix");
  }

  @NotNull
  @Override
  public RefactoringActionHandler getHandler() {
    return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiElement elementToRefactor = getElementToRefactor(previewDescriptor.getPsiElement());
    if (elementToRefactor instanceof PsiClass aClass && aClass.getParent() instanceof PsiClass) {
      String className = aClass.getName();
      String key = aClass.hasModifierProperty(PsiModifier.STATIC)
                   ? "move.inner.class.to.upper.level.or.another.class.preview"
                   : "move.inner.class.to.upper.level.preview";
      return new IntentionPreviewInfo.Html(JavaRefactoringBundle.message(key, className));
    }
    return IntentionPreviewInfo.EMPTY;
  }
}