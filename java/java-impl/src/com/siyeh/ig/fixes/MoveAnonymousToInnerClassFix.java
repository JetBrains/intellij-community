/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class MoveAnonymousToInnerClassFix extends RefactoringInspectionGadgetsFix {

  private final @IntentionFamilyName String name;

  public MoveAnonymousToInnerClassFix(@IntentionFamilyName String name) {
    this.name = name;
  }

  public MoveAnonymousToInnerClassFix() {
    name = InspectionGadgetsBundle.message("move.anonymous.to.inner.quickfix");
  }

  @Override
  public @NotNull String getFamilyName() {
    return name;
  }

  @Override
  public @NotNull RefactoringActionHandler getHandler() {
    return JavaRefactoringActionHandlerFactory.getInstance().createAnonymousToInnerHandler();
  }

  @Override
  public PsiElement getElementToRefactor(PsiElement element) {
    if (element instanceof PsiNewExpression newExpression) {
      PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      if (anonymousClass != null) return anonymousClass;
    }
    return element instanceof PsiClass ? element : element.getParent();
  }
}
