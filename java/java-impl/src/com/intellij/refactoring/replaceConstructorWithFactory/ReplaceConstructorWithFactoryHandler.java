// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryHandler implements RefactoringActionHandler, ContextAwareActionHandler {

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.constructor"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
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

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (elements[0] instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)elements[0];
      invoke(method, editor, project);
    }
    else if (elements[0] instanceof PsiClass) {
      invoke((PsiClass)elements[0], editor, project);
    }

  }

  private static void invoke(PsiClass aClass, Editor editor, Project project) {
    String qualifiedName = aClass.getQualifiedName();
    if(qualifiedName == null) {
      showJspOrLocalClassMessage(editor, project);
      return;
    }
    if (!checkAbstractClassOrInterfaceMessage(aClass, editor, project)) return;
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length > 0) {
      String message =
              JavaRefactoringBundle.message("class.does.not.have.implicit.default.constructor", aClass.getQualifiedName()) ;
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
      return;
    }
    final int answer = Messages.showYesNoDialog(project,
                                                JavaRefactoringBundle.message("would.you.like.to.replace.default.constructor.of.0.with.factory.method", aClass.getQualifiedName()),
                                                getRefactoringName(), Messages.getQuestionIcon()
    );
    if (answer != Messages.YES) return;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;
    new ReplaceConstructorWithFactoryDialog(project, null, aClass).show();
  }

  private static void showJspOrLocalClassMessage(Editor editor, Project project) {
    String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("refactoring.is.not.supported.for.local.and.jsp.classes"));
    CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
  }
  private static boolean checkAbstractClassOrInterfaceMessage(PsiClass aClass, Editor editor, Project project) {
    if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
    String message = RefactoringBundle.getCannotRefactorMessage(aClass.isInterface() ?
                                                                JavaRefactoringBundle.message("class.is.interface", aClass.getQualifiedName()) :
                                                                JavaRefactoringBundle.message("class.is.abstract", aClass.getQualifiedName()));
    CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
    return false;
  }

  void invoke(final PsiMethod method, Editor editor, Project project) {
    if (!method.isConstructor()) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("method.is.not.a.constructor"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
      return;
    }

    PsiClass aClass = method.getContainingClass();
    if(aClass == null || aClass.getQualifiedName() == null) {
      showJspOrLocalClassMessage(editor, project);
      return;
    }

    if (!checkAbstractClassOrInterfaceMessage(aClass, editor, project)) return;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;
    new ReplaceConstructorWithFactoryDialog(project, method, method.getContainingClass()).show();
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    PsiElement element = BaseRefactoringAction.getElementAtCaret(editor, file);
    return RefactoringActionContextUtil.getJavaMethodHeader(element) != null;
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("replace.constructor.with.factory.method.title");
  }
}
