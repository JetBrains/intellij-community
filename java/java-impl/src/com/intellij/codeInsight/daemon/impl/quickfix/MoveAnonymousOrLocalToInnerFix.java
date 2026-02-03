// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandlerOnPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveAnonymousOrLocalToInnerFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public MoveAnonymousOrLocalToInnerFix(@NotNull PsiClass aClass) {
    super(aClass);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    RefactoringActionHandlerOnPsiElement<PsiClass> handler =
      JavaRefactoringActionHandlerFactory.getInstance().createAnonymousToInnerHandler();
    handler.invoke(project, editor, (PsiClass)startElement);
  }

  @Override
  public @NotNull String getText() {
    PsiClass psiClass = (PsiClass)getStartElement();
    return psiClass instanceof PsiAnonymousClass ? JavaRefactoringBundle.message("convert.anonymous.to.inner.fix.name") :
           JavaRefactoringBundle.message("convert.local.to.inner.fix.name");
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaRefactoringBundle.message("convert.anonymous.or.local.to.inner.fix.name");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
