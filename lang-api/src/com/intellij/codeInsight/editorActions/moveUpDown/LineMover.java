package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

class LineMover extends StatementUpDownMover {

  public boolean checkAvailable(@NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final MoveInfo info, final boolean down) {
    LineRange range = StatementUpDownMover.getLineRangeFromSelection(editor);

    final int maxLine = editor.offsetToLogicalPosition(editor.getDocument().getTextLength()).line;
    if (range.startLine == 0 && !down) return false;
    if (range.endLine >= maxLine && down) return false;

    int nearLine = down ? range.endLine : range.startLine - 1;
    info.toMove = range;
    info.toMove2 = new LineRange(nearLine, nearLine + 1);

    return true;
  }

  protected static Pair<PsiElement, PsiElement> getElementRange(final PsiElement parent,
                                                                PsiElement element1,
                                                                PsiElement element2) {
    if (PsiTreeUtil.isAncestor(element1, element2, false) || PsiTreeUtil.isAncestor(element2, element1, false)) {
      return Pair.create(parent, parent);
    }
    // find nearset children that are parents of elements
    while (element1 != null && element1.getParent() != parent) {
      element1 = element1.getParent();
    }
    while (element2 != null && element2.getParent() != parent) {
      element2 = element2.getParent();
    }
    if (element1 == null || element2 == null) return null;
    if (element1 != element2) {
      assert element1.getTextRange().getEndOffset() <= element2.getTextRange().getStartOffset() : element1.getTextRange() + "-"+element2.getTextRange()+element1+element2;
    }
    return Pair.create(element1, element2);
  }
}
