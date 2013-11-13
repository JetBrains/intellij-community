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
package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryHandler
        implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("replace.constructor.with.factory.method.title");
  private Project myProject;

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.constructor"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
        return;
      }

      if (element instanceof PsiReferenceExpression) {
        final PsiElement psiElement = ((PsiReferenceExpression)element).resolve();
        if (psiElement instanceof PsiMethod && ((PsiMethod) psiElement).isConstructor()) {
          invoke(project, new PsiElement[] { psiElement }, dataContext);
          return;
        }
      }
      else if (element instanceof PsiConstructorCall) {
        final PsiConstructorCall constructorCall = (PsiConstructorCall)element;
        final PsiMethod method = constructorCall.resolveConstructor();
        if (method != null) {
          invoke(project, new PsiElement[] { method }, dataContext);
          return;
        }
        // handle default constructor
        if (element instanceof PsiNewExpression) {
          final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)element).getClassReference();
          if (classReference != null) {
            final PsiElement classElement = classReference.resolve();
            if (classElement instanceof PsiClass) {
              invoke(project, new PsiElement[] { classElement }, dataContext);
              return;
            }
          }
        }
      }

      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)
          && ((PsiClass) element).getConstructors().length == 0) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      if (element instanceof PsiMethod && ((PsiMethod) element).isConstructor()) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    myProject = project;
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (elements[0] instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)elements[0];
      invoke(method, editor);
    }
    else if (elements[0] instanceof PsiClass) {
      invoke((PsiClass)elements[0], editor);
    }

  }

  private void invoke(PsiClass aClass, Editor editor) {
    String qualifiedName = aClass.getQualifiedName();
    if(qualifiedName == null) {
      showJspOrLocalClassMessage(editor);
      return;
    }
    if (!checkAbstractClassOrInterfaceMessage(aClass, editor)) return;
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length > 0) {
      String message =
              RefactoringBundle.message("class.does.not.have.implicit.default.constructor", aClass.getQualifiedName()) ;
      CommonRefactoringUtil.showErrorHint(myProject, editor, message, REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
      return;
    }
    final int answer = Messages.showYesNoDialog(myProject,
                                                RefactoringBundle.message("would.you.like.to.replace.default.constructor.of.0.with.factory.method", aClass.getQualifiedName()),
                                                REFACTORING_NAME, Messages.getQuestionIcon()
    );
    if (answer != Messages.YES) return;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, aClass)) return;
    new ReplaceConstructorWithFactoryDialog(myProject, null, aClass).show();
  }

  private void showJspOrLocalClassMessage(Editor editor) {
    String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("refactoring.is.not.supported.for.local.and.jsp.classes"));
    CommonRefactoringUtil.showErrorHint(myProject, editor, message, REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
  }
  private boolean checkAbstractClassOrInterfaceMessage(PsiClass aClass, Editor editor) {
    if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
    String message = RefactoringBundle.getCannotRefactorMessage(aClass.isInterface() ?
                                                                RefactoringBundle.message("class.is.interface", aClass.getQualifiedName()) :
                                                                RefactoringBundle.message("class.is.abstract", aClass.getQualifiedName()));
    CommonRefactoringUtil.showErrorHint(myProject, editor, message, REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
    return false;
  }

  private void invoke(final PsiMethod method, Editor editor) {
    if (!method.isConstructor()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("method.is.not.a.constructor"));
      CommonRefactoringUtil.showErrorHint(myProject, editor, message, REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
      return;
    }

    PsiClass aClass = method.getContainingClass();
    if(aClass == null || aClass.getQualifiedName() == null) {
      showJspOrLocalClassMessage(editor);
      return;
    }

    if (!checkAbstractClassOrInterfaceMessage(aClass, editor)) return;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, method)) return;
    new ReplaceConstructorWithFactoryDialog(myProject, method, method.getContainingClass()).show();
  }
}
