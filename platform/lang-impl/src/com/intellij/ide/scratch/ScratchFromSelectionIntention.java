// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ide.scratch.ScratchFileActions.doCreateNewScratch;

public final class ScratchFromSelectionIntention implements IntentionAction {
  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return LangBundle.message("intention.family.scratch.from.selection");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             Editor editor,
                             PsiFile psiFile) {
    if (editor == null) return false;
    if (!EditorUtil.isRealFileEditor(editor)) return false;
    return !EditorUtil.getSelectionInAnyMode(editor).isEmpty();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (editor == null || EditorUtil.getSelectionInAnyMode(editor).isEmpty()) return;
    ScratchFileCreationHelper.Context context = ScratchFileActions.createContext(
      project, psiFile, editor, ((EditorEx)editor).getDataContext());
    doCreateNewScratch(project, context);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
