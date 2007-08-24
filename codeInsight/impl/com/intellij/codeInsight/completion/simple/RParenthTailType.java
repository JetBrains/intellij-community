/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.TailType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public abstract class RParenthTailType extends TailType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.simple.RParenthSimpleTailType");

  private static TextRange getRangeToCheckParensBalance(PsiFile file, final Document document, int startOffset){
    PsiElement element = file.findElementAt(startOffset);
    element = PsiTreeUtil.getParentOfType(element, PsiStatement.class, false);
    return element == null ? new TextRange(0, document.getTextLength()) : element.getTextRange();
  }

  protected abstract boolean isSpaceWithinParentheses(CodeStyleSettings styleSettings, Editor editor, final int tailOffset);

  public int processTail(final Editor editor, int tailOffset) {
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(editor.getProject());
    int existingRParenthOffset = getExistingRParenthOffset(editor, tailOffset);

    boolean spaceWithinParens = isSpaceWithinParentheses(styleSettings, editor, tailOffset);
    if (existingRParenthOffset < 0){
      if (spaceWithinParens){
        tailOffset = insertChar(editor, tailOffset, ' ');
      }
      editor.getDocument().insertString(tailOffset, ")");
      return moveCaret(editor, tailOffset, 1);
    }
    if (spaceWithinParens && tailOffset == existingRParenthOffset) {
      insertChar(editor, tailOffset, ' ');
    }
    return moveCaret(editor, existingRParenthOffset, 1);
  }

  @NonNls
  public String toString() {
    return "RParenth";
  }

  private static int getExistingRParenthOffset(final Editor editor, final int tailOffset) {
    final Document document = editor.getDocument();
    final CharSequence charsSequence = document.getCharsSequence();
    EditorHighlighter highlighter = ((EditorEx) editor).getHighlighter();

    int existingRParenthOffset = -1;
    for(HighlighterIterator iterator = highlighter.createIterator(tailOffset); !iterator.atEnd(); iterator.advance()){
      final IElementType tokenType = iterator.getTokenType();

      if ((!(tokenType instanceof IJavaElementType) || !JavaTokenType.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(tokenType)) &&
          tokenType != TokenType.WHITE_SPACE) {
        final int start = iterator.getStart();
        if (iterator.getEnd() == start + 1 &&  ')' == charsSequence.charAt(start)) {
          existingRParenthOffset = start;
        }
        break;
      }
    }

    if (existingRParenthOffset >= 0){
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(editor.getProject());
      psiDocumentManager.commitDocument(document);
      TextRange range = getRangeToCheckParensBalance(psiDocumentManager.getPsiFile(document), document, editor.getCaretModel().getOffset());
      int balance = calcParensBalance(document, highlighter, range.getStartOffset(), range.getEndOffset());
      if (balance > 0){
        return -1;
      }
    }
    return existingRParenthOffset;
  }

  private static int calcParensBalance(Document document, EditorHighlighter highlighter, int rangeStart, int rangeEnd){
    LOG.assertTrue(0 <= rangeStart);
    LOG.assertTrue(rangeStart <= rangeEnd);
    LOG.assertTrue(rangeEnd <= document.getTextLength());

    HighlighterIterator iterator = highlighter.createIterator(rangeStart);
    int balance = 0;
    while(!iterator.atEnd() && iterator.getStart() < rangeEnd){
      IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LPARENTH){
        balance++;
      }
      else if (tokenType == JavaTokenType.RPARENTH){
        balance--;
      }
      iterator.advance();
    }
    return balance;
  }

}
