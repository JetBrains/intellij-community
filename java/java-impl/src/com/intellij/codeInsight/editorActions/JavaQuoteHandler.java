// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class JavaQuoteHandler extends SimpleTokenSetQuoteHandler implements JavaLikeQuoteHandler, MultiCharQuoteHandler {
  private final TokenSet myConcatenableStrings = TokenSet.create(JavaTokenType.STRING_LITERAL);
  private final TokenSet myAppropriateElementTypeForLiteral = TokenSet.orSet(
    JavaDocTokenType.ALL_JAVADOC_TOKENS,
    ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET, ElementType.TEXT_LITERALS,
    TokenSet.create(JavaTokenType.SEMICOLON, JavaTokenType.COMMA, JavaTokenType.RPARENTH, JavaTokenType.RBRACKET, JavaTokenType.RBRACE));

  public JavaQuoteHandler() {
    super(TokenSet.orSet(ElementType.TEXT_LITERALS, TokenSet.create(JavaDocTokenType.DOC_TAG_VALUE_QUOTE)));
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    boolean openingQuote = super.isOpeningQuote(iterator, offset);
    if (openingQuote) {
      // check escape next
      if (!iterator.atEnd()) {
        iterator.retreat();
        if (!iterator.atEnd() && StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(iterator.getTokenType())) {
          openingQuote = false;
        }
        iterator.advance();
      }
    }
    return openingQuote;
  }

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    if (iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_LITERAL) {
      int start = iterator.getStart(), end = iterator.getEnd();
      return end - start >= 5 && offset >= end - 3;
    }
    boolean closingQuote = super.isClosingQuote(iterator, offset);
    if (closingQuote) {
      // check escape next
      if (!iterator.atEnd()) {
        iterator.advance();
        if (!iterator.atEnd() && StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(iterator.getTokenType())) {
          closingQuote = false;
        }
        iterator.retreat();
      }
    }
    return closingQuote;
  }

  @NotNull
  @Override
  @SuppressWarnings("SpellCheckingInspection")
  public TokenSet getConcatenatableStringTokenTypes() {
    return myConcatenableStrings;
  }

  @Override
  public String getStringConcatenationOperatorRepresentation() {
    return "+";
  }

  @Override
  public TokenSet getStringTokenTypes() {
    return myLiteralTokenSet;
  }

  @Override
  public boolean isAppropriateElementTypeForLiteral(@NotNull IElementType tokenType) {
    return myAppropriateElementTypeForLiteral.contains(tokenType);
  }

  @Override
  public boolean needParenthesesAroundConcatenation(PsiElement element) {
    // example code: "some string".length() must become ("some" + " string").length()
    return element.getParent() instanceof PsiLiteralExpression && element.getParent().getParent() instanceof PsiReferenceExpression;
  }

  @Nullable
  @Override
  public CharSequence getClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    return iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_LITERAL && offset == iterator.getStart() + 3 ? "\"\"\"" : null;
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    if (iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_LITERAL) {
      Document document = editor.getDocument();
      Project project = editor.getProject();
      PsiFile file = project == null ? null : PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null || !HighlightingFeature.TEXT_BLOCKS.isAvailable(file)) return false;
      String text = document.getText();
      boolean hasOpenQuotes = StringUtil.equals(text.substring(iterator.getStart(), offset + 1), "\"\"\"");
      if (hasOpenQuotes) {
        boolean hasCloseQuotes = StringUtil.contains(text.substring(offset + 1, iterator.getEnd()), "\"\"\"");
        if (!hasCloseQuotes) return true;
        // check if parser interpreted next text block start quotes as end quotes for the current one
        int nTextBlockQuotes = StringUtil.getOccurrenceCount(text.substring(iterator.getEnd()), "\"\"\"");
        return nTextBlockQuotes % 2 != 0;
      }
    }
    return super.hasNonClosedLiteral(editor, iterator, offset);
  }

  @Override
  public void insertClosingQuote(@NotNull Editor editor, int offset, @NotNull PsiFile file, @NotNull CharSequence closingQuote) {
    editor.getDocument().insertString(offset, "\n\"\"\"");
    Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    PsiJavaToken token = ObjectUtils.tryCast(file.findElementAt(offset), PsiJavaToken.class);
    if (token == null) return;
    PsiLiteralExpression textBlock = ObjectUtils.tryCast(token.getParent(), PsiLiteralExpression.class);
    if (textBlock == null) return;
    CodeStyleManager.getInstance(project).reformat(textBlock);
    editor.getCaretModel().moveToOffset(textBlock.getTextRange().getEndOffset() - 3);
  }
}