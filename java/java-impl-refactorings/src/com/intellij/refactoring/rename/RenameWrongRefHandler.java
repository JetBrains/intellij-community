// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.daemon.impl.quickfix.RenameWrongRefFix;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

public final class RenameWrongRefHandler implements RenameHandler {

  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (editor == null || file == null || project == null) return false;
    return isAvailable(project, editor, file);
  }

  public static boolean isAvailable(Project project, Editor editor, PsiFile file) {
    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    return reference instanceof PsiReferenceExpression expression &&
           new RenameWrongRefFix(expression, true).isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    if (reference instanceof PsiReferenceExpression expression) {
      WriteCommandAction.writeCommandAction(project).run(() -> new RenameWrongRefFix(expression).invoke(project, editor, file));
    }
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {}
}
