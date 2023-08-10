// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * @author Dennis.Ushakov
 */
class MoveLineHandler extends BaseMoveHandler {
  MoveLineHandler(boolean down) {
    super(down);
  }

  @Nullable
  @Override
  protected PsiFile getPsiFile(@NotNull Project project, @NotNull Editor editor) {
    // "Move line" performs simple textual change, and doesn't use PsiFile at all, there's no need to commit the document.
    return null;
  }

  @Override
  @Nullable
  protected MoverWrapper getSuitableMover(@NotNull final Editor editor, @Nullable final PsiFile file) {
    final StatementUpDownMover.MoveInfo info = new StatementUpDownMover.MoveInfo();
    info.indentTarget = false;
    if (LineMover.checkLineMoverAvailable(editor, info, isDown)) {
      final StatementUpDownMover mover = new LineMover();
      return new MoverWrapper(mover, info, isDown);
    }
    return null;
  }

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    // We perform line movement only once for each line (only for the first caret in line)
    return super.isEnabledForCaret(editor, caret, dataContext) && isCurrentCaretFirstInItsLine(editor);
  }

  private boolean isCurrentCaretFirstInItsLine(@NotNull Editor editor) {
    final Caret currentCaret = editor.getCaretModel().getCurrentCaret();
    final int currentCaretLine = currentCaret.getLogicalPosition().line;
    final Caret firstCaretInLine = editor.getCaretModel().getAllCarets().stream()
      .filter(it -> it.getLogicalPosition().line == currentCaretLine)
      .min(Comparator.comparing(Caret::getOffset))
      .get();
    return currentCaret == firstCaretInLine;
  }
}
