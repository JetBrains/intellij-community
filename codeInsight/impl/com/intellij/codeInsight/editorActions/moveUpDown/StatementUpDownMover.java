package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public abstract class StatementUpDownMover {
  public static final ExtensionPointName<StatementUpDownMover> STATEMENT_UP_DOWN_MOVER_EP = ExtensionPointName.create("com.intellij.statementUpDownMover");

  public static class MoveInfo {
    @NotNull
    public LineRange toMove;

    @Nullable // if move is illegal
    public LineRange toMove2;

    public RangeMarker range1;
    public RangeMarker range2;

    public boolean indentSource;
  }

  public abstract boolean checkAvailable(@NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final MoveInfo info, final boolean down);

  public void beforeMove(@NotNull final Editor editor, @NotNull final MoveInfo info, final boolean down) {
  }

  public void afterMove(@NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final MoveInfo info, final boolean down) {
  }

  public static int getLineStartSafeOffset(final Document document, int line) {
    if (line == document.getLineCount()) return document.getTextLength();
    return document.getLineStartOffset(line);
  }
}
