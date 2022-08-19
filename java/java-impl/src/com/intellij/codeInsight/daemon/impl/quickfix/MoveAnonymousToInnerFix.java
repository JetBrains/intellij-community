// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandlerOnPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveAnonymousToInnerFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public MoveAnonymousToInnerFix(@NotNull PsiAnonymousClass aClass) {
    super(aClass);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    RefactoringActionHandlerOnPsiElement<PsiAnonymousClass> handler =
      JavaRefactoringActionHandlerFactory.getInstance().createAnonymousToInnerHandler();
    handler.invoke(project, editor, (PsiAnonymousClass)startElement);
  }

  @Override
  public @NotNull String getText() {
    return JavaRefactoringBundle.message("convert.anonymous.to.inner.fix.name");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
