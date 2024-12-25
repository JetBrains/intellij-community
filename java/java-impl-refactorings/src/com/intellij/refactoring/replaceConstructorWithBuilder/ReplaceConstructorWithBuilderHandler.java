// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceConstructorWithBuilderHandler implements RefactoringActionHandler {
  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    final PsiClass psiClass = getParentNamedClass(element);
    if (psiClass == null) {
      showErrorMessage(JavaRefactoringBundle.message("replace.constructor.builder.error.caret.position"), project, editor);
      return;
    }

    final PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) {
      showErrorMessage(JavaRefactoringBundle.message("replace.constructor.builder.error.no.constructors"), project, editor);
      return;
    }

    new ReplaceConstructorWithBuilderDialog(project, constructors).show();
  }

  public static @Nullable PsiClass getParentNamedClass(PsiElement element) {
    if (element != null) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        final PsiElement resolve = ((PsiJavaCodeReferenceElement)parent).resolve();
        if (resolve instanceof PsiClass) return (PsiClass)resolve;
      }
    }
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass instanceof PsiAnonymousClass) {
      return getParentNamedClass(psiClass);
    }
    return psiClass;
  }

  @Override
  public void invoke(final @NotNull Project project, final PsiElement @NotNull [] elements, final DataContext dataContext) {
    throw new UnsupportedOperationException();
  }

  private static void showErrorMessage(@NlsContexts.DialogMessage String message, Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, JavaRefactoringBundle.message("replace.constructor.with.builder"), HelpID.REPLACE_CONSTRUCTOR_WITH_BUILDER);
  }
}
