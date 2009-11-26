/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeSignatureHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("changeSignature.refactoring.name");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = ChangeSignatureTargetUtil.findTargetMember(file, editor);
    if (element == null) {
      element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    invokeOnElement(project, editor, element);
  }

  private static void invokeOnElement(Project project, Editor editor, PsiElement element) {
    if (element instanceof PsiMethod) {
      invoke((PsiMethod) element, project, editor);
    }
    else if (element instanceof PsiClass) {
      invoke((PsiClass) element, editor);
    }
    else {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method.or.class.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.CHANGE_SIGNATURE);
    }
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    if (elements.length != 1) return;
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    invokeOnElement(project, editor, elements[0]);
  }

  private static void invoke(final PsiMethod method, final Project project, @Nullable final Editor editor) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;

    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (newMethod == null) return;

    if (!newMethod.equals(method)) {
      invoke(newMethod, project, editor);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;

    final PsiClass containingClass = method.getContainingClass();
    final PsiReferenceExpression refExpr = editor != null ? TargetElementUtil.findReferenceExpression(editor) : null;
    final ChangeSignatureDialog dialog = new ChangeSignatureDialog(project, method, containingClass != null && !containingClass.isInterface(),
                                                                   refExpr);
    dialog.show();
  }

  private static void invoke(final PsiClass aClass, Editor editor) {
    final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
    Project project = aClass.getProject();
    if (typeParameterList == null) {
      final String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("changeClassSignature.no.type.parameters"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.CHANGE_CLASS_SIGNATURE);
      return;
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

    ChangeClassSignatureDialog dialog = new ChangeClassSignatureDialog(aClass);
    dialog.show();
  }
}
