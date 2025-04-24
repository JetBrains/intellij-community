// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class JavaCatchBlockMover extends LineMover {

  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (!super.checkAvailable(editor, file, info, down)) return false;

    final Document document = editor.getDocument();
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int startOffset;
    final int endOffset;
    if (selectionModel.hasSelection()) {
      startOffset = selectionModel.getSelectionStart();
      endOffset = selectionModel.getSelectionEnd();
    }
    else {
      startOffset = document.getLineStartOffset(info.toMove.startLine);
      endOffset = getLineStartSafeOffset(document, info.toMove.endLine);
    }
    final PsiElement element = file.findElementAt(startOffset);
    if (element == null) return false;
    final PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class, true, PsiMember.class);
    if (tryStatement == null) return false;
    PsiCatchSection firstToMove = null;
    PsiCatchSection lastToMove = null;
    for (PsiCatchSection catchSection : tryStatement.getCatchSections()) {
      final int offset = catchSection.getTextOffset();
      final PsiElement child = catchSection.getFirstChild();
      if (!(child instanceof PsiKeyword)) return info.prohibitMove();
      if (offset >= startOffset && offset < endOffset || child.getTextRange().contains(startOffset)) {
        if (firstToMove == null) firstToMove = catchSection;
        lastToMove = catchSection;
      }
    }
    if (firstToMove == null) return false;
    if (!sanityCheck(firstToMove)) {
      return info.prohibitMove();
    }
    if (element instanceof PsiWhiteSpace && element.getNextSibling() instanceof PsiStatement
        || PsiTreeUtil.getParentOfType(element, PsiStatement.class, true, PsiMember.class) != tryStatement) {
      // nonsensical selection
      return info.prohibitMove();
    }

    final PsiCatchSection sibling = down
                                    ? PsiTreeUtil.getNextSiblingOfType(lastToMove, PsiCatchSection.class)
                                    : PsiTreeUtil.getPrevSiblingOfType(firstToMove, PsiCatchSection.class);
    if (sibling == null) return info.prohibitMove();

    info.toMove = new LineRange(firstToMove, lastToMove, document);
    info.toMove2 = new LineRange(sibling, sibling, document);
    if (down ? info.toMove.endLine > info.toMove2.startLine : info.toMove2.endLine > info.toMove.startLine) {
      info.toMove = new LineRange(info.toMove.startLine, info.toMove.endLine - 1);
      info.toMove2 = new LineRange(info.toMove2.startLine, info.toMove2.endLine - 1);
    }
    info.indentSource = false;
    info.indentTarget = false;
    return true;
  }

  private static boolean sanityCheck(PsiCatchSection catchSection) {
    final PsiCatchSection[] catchSections = catchSection.getTryStatement().getCatchSections();
    if (catchSections.length < 2) return false;
    final boolean newLine = containsNewLine(catchSections[0].getPrevSibling());
    for (int i = 1; i < catchSections.length; i++) {
      if (newLine != containsNewLine(catchSections[i].getPrevSibling())) return false;
    }
    return true;
  }

  private static boolean containsNewLine(PsiElement element) {
    return element instanceof PsiWhiteSpace && element.getText().contains("\n");
  }
}
