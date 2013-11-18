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
package com.intellij.refactoring.invertBoolean;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class InvertBooleanHandler implements RefactoringActionHandler {
  static final String REFACTORING_NAME = RefactoringBundle.message("invert.boolean.title");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof PsiMethod) {
      invoke((PsiMethod)element, project, editor);
    }
    else if (element instanceof PsiVariable) {
      invoke((PsiVariable)element, project, editor);
    }
    else {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("error.wrong.caret.position.method.or.variable.name")), REFACTORING_NAME, HelpID.INVERT_BOOLEAN);
    }
  }

  private static void invoke(PsiVariable var, final Project project, Editor editor) {
    final PsiType returnType = var.getType();
    if (!PsiType.BOOLEAN.equals(returnType)) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invert.boolean.wrong.type")), REFACTORING_NAME, HelpID.INVERT_BOOLEAN);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, var)) return;
    if (var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)((PsiParameter)var).getDeclarationScope();
      final PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
       if (superMethod != null) {
         var = superMethod.getParameterList().getParameters()[method.getParameterList().getParameterIndex((PsiParameter)var)];
       }
    }

    new InvertBooleanDialog(var).show();
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @NotNull DataContext dataContext) {
    if (elements.length == 1) {
      if (elements[0] instanceof PsiMethod) {
        invoke((PsiMethod)elements[0], project, null);
      }
      else if (elements[0] instanceof PsiVariable) {
        invoke((PsiVariable)elements[0], project, null);
      }
    }
  }

  private static void invoke(PsiMethod method, final Project project, Editor editor) {
    final PsiType returnType = method.getReturnType();
    if (!PsiType.BOOLEAN.equals(returnType)) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invert.boolean.wrong.type")), REFACTORING_NAME, HelpID.INVERT_BOOLEAN);
      return;
    }

    final PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (superMethod != null) method = superMethod;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;

    new InvertBooleanDialog(method).show();
  }
}
