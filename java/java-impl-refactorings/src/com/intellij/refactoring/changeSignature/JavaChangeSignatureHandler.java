// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.JavaTargetElementEvaluator;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.refactoring.changeSignature.inplace.InplaceChangeSignature;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class JavaChangeSignatureHandler implements ChangeSignatureHandler {

  private static final Logger LOG = Logger.getInstance(JavaChangeSignatureHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = findTargetMember(file, editor);
    if (element == null) {
      element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    invokeOnElement(project, editor, element);
  }

  private static void invokeOnElement(Project project, @Nullable Editor editor, PsiElement element) {
    if (element instanceof PsiMethod && ((PsiMethod)element).getNameIdentifier() != null) {
      invoke((PsiMethod) element, project, editor);
    }
    else if (element instanceof PsiClass) {
      invoke((PsiClass) element, editor);
    }
    else {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method.or.class.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, RefactoringBundle.message("changeSignature.refactoring.name"), HelpID.CHANGE_SIGNATURE);
    }
  }

  @Override
  public void invoke(@NotNull final Project project, final PsiElement @NotNull [] elements, @Nullable final DataContext dataContext) {
    if (elements.length != 1) return;
    Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    invokeOnElement(project, editor, elements[0]);
  }

  @Nullable
  @Override
  public String getTargetNotFoundMessage() {
    return RefactoringBundle.message("error.wrong.caret.position.method.or.class.name");
  }

  private static void invoke(@NotNull PsiMethod method, @NotNull Project project, @Nullable final Editor editor) {
    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(method);
    if (newMethod == null) return;

    if (!newMethod.equals(method)) {
      ChangeSignatureUtil.invokeChangeSignatureOn(newMethod, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;

    final PsiClass containingClass = method.getContainingClass();
    final PsiReferenceExpression refExpr = editor != null ? JavaTargetElementEvaluator.findReferenceExpression(editor) : null;
    final boolean allowDelegation = containingClass != null && 
                                    (!containingClass.isInterface() || PsiUtil.isLanguageLevel8OrHigher(containingClass)) &&
                                    !JavaPsiRecordUtil.isCanonicalConstructor(method);
    InplaceChangeSignature inplaceChangeSignature = editor != null ? InplaceChangeSignature.getCurrentRefactoring(editor) : null;
    ChangeInfo initialChange = inplaceChangeSignature != null ? inplaceChangeSignature.getStableChange() : null;

    boolean isInplace = Registry.is("inplace.change.signature") &&
                        editor != null && editor.getSettings().isVariableInplaceRenameEnabled() &&
                        (initialChange == null || initialChange.getMethod() != method) &&
                        refExpr == null;
    PsiIdentifier nameIdentifier = method.getNameIdentifier();
    LOG.assertTrue(nameIdentifier != null);
    if (isInplace) {
      CommandProcessor.getInstance().executeCommand(project, () -> new InplaceChangeSignature(project, editor, nameIdentifier),
                                                    RefactoringBundle.message("changeSignature.refactoring.name"), null);
    }
    else {
      JavaMethodDescriptor methodDescriptor = new JavaMethodDescriptor(method);
      if (initialChange != null) {
        JavaChangeInfo currentInfo = (JavaChangeInfo)inplaceChangeSignature.getCurrentInfo();
        if (currentInfo != null) {
          methodDescriptor = new JavaMethodDescriptor(method) {
            @Override
            public String getName() {
              return currentInfo.getNewName();
            }

            @Override
            public @NotNull List<ParameterInfoImpl> getParameters() {
              return Arrays.asList((ParameterInfoImpl[])currentInfo.getNewParameters());
            }

            @Override
            public @NotNull String getVisibility() {
              return currentInfo.getNewVisibility();
            }


            @Override
            public int getParametersCount() {
              return currentInfo.getNewParameters().length;
            }

            @Nullable
            @Override
            public String getReturnTypeText() {
              return currentInfo.getNewReturnType().getTypeText();
            }
          };
        }
        inplaceChangeSignature.cancel();
      }
      final DialogWrapper dialog = new JavaChangeSignatureDialog(project, methodDescriptor, allowDelegation, refExpr == null ? method : refExpr);
      dialog.show();
    }
  }

  private static void invoke(final PsiClass aClass, Editor editor) {
    final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
    Project project = aClass.getProject();
    if (typeParameterList == null) {
      final String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("changeClassSignature.no.type.parameters"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, RefactoringBundle.message("changeSignature.refactoring.name"), HelpID.CHANGE_CLASS_SIGNATURE);
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

  @Override
  public PsiElement findTargetMember(@NotNull PsiElement element) {
    if (PsiTreeUtil.getParentOfType(element, PsiParameterList.class) != null) {
      return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    }
    PsiRecordHeader header = PsiTreeUtil.getParentOfType(element, PsiRecordHeader.class);
    if (header != null) {
      PsiClass aClass = header.getContainingClass();
      if (aClass != null) {
        return JavaPsiRecordUtil.findCanonicalConstructor(aClass);
      }
      return null;
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
    if (elementParent instanceof PsiClass psiClass && psiClass.getNameIdentifier() == element) {
      if (psiClass.isAnnotationType() || psiClass.isEnum()) {
        return null;
      }
      if (psiClass.isRecord()) {
        return JavaPsiRecordUtil.findCanonicalConstructor(psiClass);
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
