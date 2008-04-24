package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;

public class JavaBackspaceHandler extends BackspaceHandlerDelegate {
  private boolean myToDeleteGt;

  public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
    int offset = editor.getCaretModel().getOffset() - 1;
    myToDeleteGt = c =='<' &&
                        file instanceof PsiJavaFile &&
                        ((PsiJavaFile)file).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0
                        && JavaTypedHandler.isAfterClassLikeIdentifierOrDot(offset, editor);
  }

  public boolean charDeleted(final char c, final PsiFile file, final Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    final CharSequence chars = editor.getDocument().getCharsSequence();
    if (editor.getDocument().getTextLength() <= offset) return false; //virtual space after end of file

    char c1 = chars.charAt(offset);
    if (c == '<' && myToDeleteGt) {
      if (c1 != '>') return true;
      handleLTDeletion(editor, offset);
      return true;
    }
    return false;
  }

  private static void handleLTDeletion(final Editor editor, final int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    while (iterator.getStart() > 0 && !JavaTypedHandlerUtil.isTokenInvalidInsideReference(iterator.getTokenType())) {
      iterator.retreat();
    }

    if (JavaTypedHandlerUtil.isTokenInvalidInsideReference(iterator.getTokenType())) iterator.advance();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LT) {
        balance++;
      }
      else if (tokenType == JavaTokenType.GT) {
        balance--;
      }
      else if (JavaTypedHandlerUtil.isTokenInvalidInsideReference(tokenType)) {
        break;
      }

      iterator.advance();
    }

    if (balance < 0) {
      editor.getDocument().deleteString(offset, offset + 1);
    }
  }
}
