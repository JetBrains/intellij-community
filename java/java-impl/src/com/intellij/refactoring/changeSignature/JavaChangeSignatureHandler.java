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
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.JavaTargetElementEvaluator;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaChangeSignatureHandler implements ChangeSignatureHandler {

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = findTargetMember(file, editor);
    if (element == null) {
      element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    invokeOnElement(project, editor, element);
  }

  private static void invokeOnElement(Project project, @Nullable Editor editor, PsiElement element) {
    if (element instanceof PsiMethod) {
      final ChangeSignatureGestureDetector detector = ChangeSignatureGestureDetector.getInstance(project);
      final PsiIdentifier nameIdentifier = ((PsiMethod)element).getNameIdentifier();
      if (nameIdentifier != null && 
          editor != null &&
          editor.getDocument().isWritable() && 
          detector.isChangeSignatureAvailable(element)) {
        detector.changeSignature(element.getContainingFile(), false);
        return;
      }
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

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, @Nullable final DataContext dataContext) {
    if (elements.length != 1) return;
    Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    invokeOnElement(project, editor, elements[0]);
  }

  @Nullable
  @Override
  public String getTargetNotFoundMessage() {
    return RefactoringBundle.message("error.wrong.caret.position.method.or.class.name");
  }

  private static void invoke(final PsiMethod method, final Project project, @Nullable final Editor editor) {
    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (newMethod == null) return;

    if (!newMethod.equals(method)) {
      ChangeSignatureUtil.invokeChangeSignatureOn(newMethod, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;

    final PsiClass containingClass = method.getContainingClass();
    final PsiReferenceExpression refExpr = editor != null ? JavaTargetElementEvaluator.findReferenceExpression(editor) : null;
    final boolean allowDelegation = containingClass != null && (!containingClass.isInterface() || PsiUtil.isLanguageLevel8OrHigher(containingClass));
    final DialogWrapper dialog = new JavaChangeSignatureDialog(project, method, allowDelegation, refExpr == null ? method : refExpr);
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

    ChangeClassSignatureDialog dialog = new ChangeClassSignatureDialog(aClass, true);
    //if (!ApplicationManager.getApplication().isUnitTestMode()){
    dialog.show();
    //}else {
    //  dialog.showAndGetOk()
    //}
  }

  @Nullable
  public PsiElement findTargetMember(PsiFile file, Editor editor) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return findTargetMember(element);
  }

  public PsiElement findTargetMember(PsiElement element) {
    if (PsiTreeUtil.getParentOfType(element, PsiParameterList.class) != null) {
      return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    }

    final PsiTypeParameterList typeParameterList = PsiTreeUtil.getParentOfType(element, PsiTypeParameterList.class);
    if (typeParameterList != null) {
      return PsiTreeUtil.getParentOfType(typeParameterList, PsiMember.class);
    }

    final PsiElement elementParent = element.getParent();
    if (elementParent instanceof PsiMethod && ((PsiMethod)elementParent).getNameIdentifier()==element) {
      final PsiClass containingClass = ((PsiMethod)elementParent).getContainingClass();
      if (containingClass != null && containingClass.isAnnotationType()) {
        return null;
      }
      return elementParent;
    }
    if (elementParent instanceof PsiClass && ((PsiClass)elementParent).getNameIdentifier()==element) {
      if (((PsiClass)elementParent).isAnnotationType() || ((PsiClass)elementParent).isEnum()) {
        return null;
      }
      return elementParent;
    }

    final PsiCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiCallExpression.class);
    if (expression != null) {
      final PsiExpression qualifierExpression;
      if (expression instanceof PsiMethodCallExpression) {
        qualifierExpression = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
      } else if (expression instanceof PsiNewExpression) {
        qualifierExpression = ((PsiNewExpression)expression).getQualifier();
      } else {
        qualifierExpression = null;
      }
      if (PsiTreeUtil.isAncestor(qualifierExpression, element, false)) {
        final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(qualifierExpression, PsiExpressionList.class);
        if (expressionList != null) {
          final PsiElement parent = expressionList.getParent();
          if (parent instanceof PsiCallExpression) {
            return ((PsiCallExpression)parent).resolveMethod();
          }
        }
      }
      else {
        return expression.resolveMethod();
      }
    }

    final PsiReferenceParameterList referenceParameterList = PsiTreeUtil.getParentOfType(element, PsiReferenceParameterList.class);
    if (referenceParameterList != null) {
      final PsiJavaCodeReferenceElement referenceElement =
        PsiTreeUtil.getParentOfType(referenceParameterList, PsiJavaCodeReferenceElement.class);
      if (referenceElement != null) {
        final PsiElement resolved = referenceElement.resolve();
        if (resolved instanceof PsiClass) {
          return resolved;
        }
        else if (resolved instanceof PsiMethod) {
          return resolved;
        }
      }
    }
    return null;
  }
}
