// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class CatchBlockMover extends LineMover {

  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (!super.checkAvailable(editor, file, info, down)) return false;

    final Document document = editor.getDocument();
    int startOffset = document.getLineStartOffset(info.toMove.startLine);
    int endOffset = document.getLineEndOffset(info.toMove.endLine);
    PsiElement element = file.findElementAt(startOffset);
    if (element == null) return false;
    PsiKeyword keyword = null;
    while (element != null && element.getTextOffset() < endOffset) {
      if (element instanceof PsiKeyword) {
        keyword = (PsiKeyword)element;
        if (keyword.getTokenType() != JavaTokenType.CATCH_KEYWORD) {
          return false;
        }
        break;
      }
      element = PsiTreeUtil.nextLeaf(element);
    }
    if (keyword == null) return false;
    final PsiElement parent = keyword.getParent();
    if (!(parent instanceof PsiCatchSection)) return false;
    final PsiCatchSection firstToMove = (PsiCatchSection)parent;

    if (!sanityCheck(firstToMove)) {
      return info.prohibitMove();
    }

    PsiCatchSection lastToMove = firstToMove;
    while (true) {
      final PsiCatchSection next = PsiTreeUtil.getNextSiblingOfType(lastToMove, PsiCatchSection.class);
      if (next == null || next.getTextRange().getStartOffset() >= endOffset) {
        break;
      }
      lastToMove = next;
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
