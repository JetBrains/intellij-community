// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public abstract class BaseMoveHandler extends EditorWriteActionHandler.ForEachCaret {
  protected final boolean isDown;

  public BaseMoveHandler(boolean down) {
    isDown = down;
  }

  @Override
  public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    final Project project = editor.getProject();
    assert project != null;
    final Document document = editor.getDocument();
    final PsiFile file = getPsiFile(project, editor);

    final MoverWrapper mover = getSuitableMover(editor, file);
    if (mover != null && mover.getInfo().toMove2 != null) {
      LineRange range = mover.getInfo().toMove;
      if ((range.startLine > 0 || isDown) && (range.endLine < document.getLineCount() || !isDown)) {
        mover.move(editor, file);
      }
    }
  }

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (editor.isViewer() || editor.isOneLineMode()) return false;
    final Project project = editor.getProject();
    if (project == null || project.isDisposed()) return false;
    return true;
  }

  protected abstract @Nullable PsiFile getPsiFile(@NotNull Project project, @NotNull Editor editor);

  protected abstract @Nullable MoverWrapper getSuitableMover(@NotNull Editor editor, @Nullable PsiFile file);
}
