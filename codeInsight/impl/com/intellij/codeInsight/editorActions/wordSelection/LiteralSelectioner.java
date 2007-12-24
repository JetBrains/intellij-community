package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.StringEscapesTokenTypes;

import java.util.List;

public class LiteralSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    PsiElement parent = e.getParent();
    return
      isStringLiteral(e) || isStringLiteral(parent);
  }

  private static boolean isStringLiteral(PsiElement element) {
    return element instanceof PsiLiteralExpression &&
           ((PsiLiteralExpression)element).getType().equalsToText("java.lang.String") && element.getText().startsWith("\"") && element.getText().endsWith("\"");
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    TextRange range = e.getTextRange();
    final StringLiteralLexer lexer = new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL);
    lexer.start(editorText, range.getStartOffset(), range.getEndOffset(),0);

    while (lexer.getTokenType() != null) {
      if (lexer.getTokenStart() <= cursorOffset && cursorOffset < lexer.getTokenEnd()) {
        if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType())) {
          result.add(new TextRange(lexer.getTokenStart(), lexer.getTokenEnd()));
        }
        else {
          TextRange word = SelectWordUtil.getWordSelectionRange(editorText, cursorOffset);
          if (word != null) {
            result.add(new TextRange(Math.max(word.getStartOffset(), lexer.getTokenStart()),
                                     Math.min(word.getEndOffset(), lexer.getTokenEnd())));
          }
        }
        break;
      }
      lexer.advance();
    }

    result.add(new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1));

    return result;
  }
}
