
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.Collections;

class InlineMethodHandler extends JavaInlineActionHandler {
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.method.title");

  private InlineMethodHandler() {
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiMethod && element.getNavigationElement() instanceof PsiMethod && element.getLanguage() == JavaLanguage.INSTANCE;
  }

  @Override
  public void inlineElement(final Project project, Editor editor, PsiElement element) {
    PsiMethod method = (PsiMethod)element.getNavigationElement();
    final PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null){
      String message;
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.to.abstract.methods", REFACTORING_NAME);
      }
      else if (method.hasModifierProperty(PsiModifier.NATIVE)) {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.to.native.methods", REFACTORING_NAME);
      }
      else {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.no.sources.attached", REFACTORING_NAME);
      }
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
      return;
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null) {
      final PsiElement refElement = reference.getElement();
      if (!isEnabledForLanguage(refElement.getLanguage())) {
        String message = RefactoringBundle
          .message("refactoring.is.not.supported.for.language", "Inline of Java method", refElement.getLanguage().getDisplayName());
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }
    boolean allowInlineThisOnly = false;
    if (InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method)) {
      if (reference != null && InlineUtil.getTailCallType(reference) != InlineUtil.TailCallType.None) {
        allowInlineThisOnly = true;
      }
      else {
        String message = RefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }

    if (reference == null && checkRecursive(method)) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.for.recursive.methods", REFACTORING_NAME);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
      return;
    }

    if (reference != null) {
      final String errorMessage = InlineMethodProcessor.checkUnableToInsertCodeBlock(methodBody, reference.getElement());
      if (errorMessage != null) {
        CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }

    if (method.isConstructor()) {
      if (method.isVarArgs()) {
        String message = RefactoringBundle.message("refactoring.cannot.be.applied.to.vararg.constructors", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_CONSTRUCTOR);
        return;
      }
      final boolean chainingConstructor = InlineUtil.isChainingConstructor(method);
      if (!chainingConstructor) {
        if (!isThisReference(reference)) {
          String message = RefactoringBundle.message("refactoring.cannot.be.applied.to.inline.non.chaining.constructors", REFACTORING_NAME);
          CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_CONSTRUCTOR);
          return;
        }
        allowInlineThisOnly = true;
      }
      if (reference != null) {
        final PsiElement refElement = reference.getElement();
        PsiCall constructorCall = refElement instanceof PsiJavaCodeReferenceElement ? RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)refElement) : null;
        if (constructorCall == null || !method.equals(constructorCall.resolveMethod())) reference = null;
      }
    }
    else {
      if (reference != null && !method.getManager().areElementsEquivalent(method, reference.resolve())) {
        reference = null;
      }
    }

    if (reference != null && PsiTreeUtil.getParentOfType(reference.getElement(), PsiImportStaticStatement.class) != null) {
      reference = null;
    }

    final boolean invokedOnReference = reference != null;
    if (!invokedOnReference) {
      final VirtualFile vFile = method.getContainingFile().getVirtualFile();
      ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(Collections.singletonList(vFile));
    }

    PsiJavaCodeReferenceElement refElement = null;
    if (reference != null) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiJavaCodeReferenceElement) {
        refElement = (PsiJavaCodeReferenceElement)referenceElement;
      }
    }
    InlineMethodDialog dialog = new InlineMethodDialog(project, method, refElement, editor, allowInlineThisOnly);
    dialog.show();
  }

  public static boolean checkRecursive(PsiMethod method) {
    return checkCalls(method.getBody(), method);
  }

  private static boolean checkCalls(PsiElement scope, PsiMethod method) {
    if (scope instanceof PsiMethodCallExpression){
      PsiMethod refMethod = (PsiMethod)((PsiMethodCallExpression)scope).getMethodExpression().resolve();
      if (method.equals(refMethod)) return true;
    }

    if (scope instanceof PsiMethodReferenceExpression) {
      if (method.equals(((PsiMethodReferenceExpression)scope).resolve())) return true;
    }

    for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
      if (checkCalls(child, method)) return true;
    }

    return false;
  }

  public static boolean isThisReference(PsiReference reference) {
    if (reference != null) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiJavaCodeReferenceElement &&
          referenceElement.getParent() instanceof PsiMethodCallExpression &&
          "this".equals(((PsiJavaCodeReferenceElement)referenceElement).getReferenceName())) {
        return true;
      }
    }
    return false;
  }
}