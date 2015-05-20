/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class IntroduceFieldPostfixTemplate extends PostfixTemplateWithExpressionSelector {
  public IntroduceFieldPostfixTemplate() {
    super("field", "myField = expr", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    IntroduceFieldHandler handler =
      ApplicationManager.getApplication().isUnitTestMode() ? getMockHandler(expression) : new IntroduceFieldHandler();
    handler.invoke(expression.getProject(), new PsiElement[]{expression}, null);
  }

  @NotNull
  private static IntroduceFieldHandler getMockHandler(@NotNull final PsiElement expression) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    assert containingClass != null;

    return new IntroduceFieldHandler() {
      // mock default settings
      @Override
      protected Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass,
                                               PsiExpression expr, PsiType type, PsiExpression[] occurrences,
                                               PsiElement anchorElement, PsiElement anchorElementIfAll) {
        return new Settings(
          "foo", (PsiExpression)expression, PsiExpression.EMPTY_ARRAY, false, false, false,
          InitializationPlace.IN_CURRENT_METHOD, PsiModifier.PRIVATE, null,
          null, false, containingClass, false, false);
      }
    };
  }
}