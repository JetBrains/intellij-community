// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

public class RenameWrongRefHandler implements RenameHandler {


  @Override
  public final boolean isAvailableOnDataContext(@NotNull final DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (editor == null || file == null || project == null) return false;
    return isAvailable(project, editor, file);
  }

  public static boolean isAvailable(Project project, Editor editor, PsiFile file) {
    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    return reference instanceof PsiReferenceExpression && new RenameWrongRefFix((PsiReferenceExpression)reference, true).isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    if (reference instanceof PsiReferenceExpression) {
      WriteCommandAction.writeCommandAction(project).run(() -> new RenameWrongRefFix((PsiReferenceExpression)reference).invoke(project, editor, file));
    }
  }

  @Override
  public void invoke(@NotNull final Project project, final PsiElement @NotNull [] elements, final DataContext dataContext) {
  }
}
