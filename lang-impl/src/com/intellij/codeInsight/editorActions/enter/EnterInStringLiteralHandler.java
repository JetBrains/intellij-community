package com.intellij.codeInsight.editorActions.enter;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.codeInsight.editorActions.QuoteHandler;
import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.codeInsight.editorActions.JavaLikeQuoteHandler;

public class EnterInStringLiteralHandler implements EnterHandlerDelegate {
  public Result preprocessEnter(final PsiFile file, final Editor editor, Ref<Integer> caretOffsetRef, final Ref<Integer> caretAdvanceRef, 
                                final DataContext dataContext, final EditorActionHandler originalHandler) {
    int caretOffset = caretOffsetRef.get().intValue();
    int caretAdvance = caretAdvanceRef.get().intValue();
    PsiElement psiAtOffset = file.findElementAt(caretOffset);
    if (psiAtOffset != null && psiAtOffset.getTextOffset() < caretOffset) {
      Document document = editor.getDocument();
      CharSequence text = document.getText();
      ASTNode token = psiAtOffset.getNode();
      final QuoteHandler fileTypeQuoteHandler = TypedHandler.getQuoteHandler(psiAtOffset.getContainingFile());
      JavaLikeQuoteHandler quoteHandler = fileTypeQuoteHandler instanceof JavaLikeQuoteHandler ?
                                                       (JavaLikeQuoteHandler) fileTypeQuoteHandler:null;

      if (quoteHandler != null &&
          quoteHandler.getConcatenatableStringTokenTypes() != null &&
          quoteHandler.getConcatenatableStringTokenTypes().contains(token.getElementType())) {
        TextRange range = token.getTextRange();
        final char literalStart = token.getText().charAt(0);
        final StringLiteralLexer lexer = new StringLiteralLexer(literalStart, token.getElementType());
        lexer.start(text, range.getStartOffset(), range.getEndOffset(),0);

        while (lexer.getTokenType() != null) {
          if (lexer.getTokenStart() < caretOffset && caretOffset < lexer.getTokenEnd()) {
            if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType())) {
              caretOffset = lexer.getTokenEnd();
            }
            break;
          }
          lexer.advance();
        }

        if (quoteHandler.needParenthesesAroundConcatenation(psiAtOffset)) {
          document.insertString(psiAtOffset.getTextRange().getEndOffset(), ")");
          document.insertString(psiAtOffset.getTextRange().getStartOffset(), "(");
          caretOffset++;
          caretAdvance++;
        }

        final String insertedFragment = literalStart + " " + quoteHandler.getStringConcatenationOperatorRepresentation();
        document.insertString(caretOffset, insertedFragment + " " + literalStart);
        caretOffset += insertedFragment.length();
        caretAdvance = 1;
        if (CodeStyleSettingsManager.getSettings(file.getProject()).BINARY_OPERATION_SIGN_ON_NEXT_LINE) {
          caretOffset -= 1;
          caretAdvance = 3;
        }
        caretOffsetRef.set(caretOffset);
        caretAdvanceRef.set(caretAdvance);
        return Result.HandledAndForceIndent;
      }
    }
    return Result.NotHandled;
  }
}
