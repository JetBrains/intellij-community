// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.inlineSuperClass;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.refactoring.inline.JavaInlineActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;

public final class InlineSuperClassRefactoringHandler extends JavaInlineActionHandler {

  @Override
  public boolean isEnabledOnElement(PsiElement element) {
    return element instanceof PsiClass;
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    if (element.getLanguage() != JavaLanguage.INSTANCE) return false;
    return true;
  }

  @Override
  public void inlineElement(final Project project, final Editor editor, final PsiElement element) {
    if (DirectClassInheritorsSearch.search((PsiClass)element).findFirst() == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, JavaRefactoringBundle.message("inline.super.no.inheritors.warning.message"),
                                          JavaRefactoringBundle.message("inline.super.class"), null);
      return;
    }
    PsiClass superClass = (PsiClass) element;
    if (!superClass.getManager().isInProject(superClass)) {
      CommonRefactoringUtil.showErrorHint(project, editor, JavaRefactoringBundle.message("inline.super.non.project.class.warning.message"),
                                          JavaRefactoringBundle.message("inline.super.class"), null);
      return;
    }

    PsiClass chosen = null;
    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null) {
      final PsiElement resolve = reference.resolve();
      if (resolve == superClass) {
        final PsiElement referenceElement = reference.getElement();
        final PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiReferenceList) {
          final PsiElement gParent = parent.getParent();
          if (gParent instanceof PsiClass) {
            chosen = (PsiClass)gParent;
          }
        }
      }
    }
    new InlineSuperClassRefactoringDialog(project, superClass, chosen).show();
  }

  @Override
  public @Nullable String getActionName(PsiElement element) {
    return JavaRefactoringBundle.message("inline.super.class.action.name");
  }
}