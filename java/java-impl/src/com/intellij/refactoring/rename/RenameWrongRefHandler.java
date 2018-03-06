/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.refactoring.rename;

import com.intellij.codeInsight.daemon.impl.quickfix.RenameWrongRefFix;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

public class RenameWrongRefHandler implements RenameHandler {


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

  public final boolean isRenaming(@NotNull final DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    if (reference instanceof PsiReferenceExpression) {
      WriteCommandAction.writeCommandAction(project).run(() -> {
        new RenameWrongRefFix((PsiReferenceExpression)reference).invoke(project, editor, file);
      });
    }
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
  }
}
