// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.definition.AbstractBasicJavaDefinitionService;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET;
import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_TEXT_LITERALS;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_LITERAL_EXPRESSION;
import static com.intellij.psi.impl.source.BasicJavaElementType.REFERENCE_EXPRESSION_SET;

public class JavaQuoteHandler extends SimpleTokenSetQuoteHandler implements JavaLikeQuoteHandler, MultiCharQuoteHandler {
  private final TokenSet myConcatenableStrings = TokenSet.create(JavaTokenType.STRING_LITERAL);
  private final ParentAwareTokenSet myAppropriateElementTypeForLiteral = ParentAwareTokenSet.orSet(
    ParentAwareTokenSet.create(JavaDocTokenType.ALL_JAVADOC_TOKENS),
    BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET, ParentAwareTokenSet.create(BASIC_TEXT_LITERALS),
    ParentAwareTokenSet.create(JavaTokenType.SEMICOLON, JavaTokenType.COMMA, JavaTokenType.RPARENTH, JavaTokenType.RBRACKET,
                               JavaTokenType.RBRACE));

  public JavaQuoteHandler() {
    super(TokenSet.orSet(BASIC_TEXT_LITERALS, TokenSet.create(JavaDocTokenType.DOC_TAG_VALUE_QUOTE, JavaDocTokenType.DOC_INLINE_CODE_FENCE, JavaDocTokenType.DOC_CODE_FENCE)));
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

  @Override
  public @NotNull TokenSet getConcatenatableStringTokenTypes() {
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
    return element != null && element.getParent() != null && element.getParent().getParent() != null &&
           BasicJavaAstTreeUtil.is(element.getParent().getNode(), BASIC_LITERAL_EXPRESSION) &&
           BasicJavaAstTreeUtil.is(element.getParent().getParent().getNode(), REFERENCE_EXPRESSION_SET);
  }

  @Override
  public @Nullable CharSequence getClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    return (iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_LITERAL || iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) 
           && offset == iterator.getStart() + 3 ? "\"\"\"" : null;
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    if (iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_LITERAL || iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
      Document document = editor.getDocument();
      Project project = editor.getProject();
      PsiFile file = project == null ? null : PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null || !testBlocksIsAvailable(file)) return false;
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

  private static boolean testBlocksIsAvailable(@NotNull PsiFile file){
      return JavaFeature.TEXT_BLOCKS.isSufficient(AbstractBasicJavaDefinitionService.getJavaDefinitionService().getLanguageLevel(file));
  }

  @Override
  public void insertClosingQuote(@NotNull Editor editor, int offset, @NotNull PsiFile file, @NotNull CharSequence closingQuote) {
    editor.getDocument().insertString(offset, "\n\"\"\"");
    Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    PsiElement token = file.findElementAt(offset);
    if (token == null) return;
    PsiElement parent = token.getParent();
    if (parent != null && BasicJavaAstTreeUtil.is(parent.getNode(), BASIC_LITERAL_EXPRESSION)) {
      CodeStyleManager.getInstance(project).reformat(parent);
      editor.getCaretModel().moveToOffset(parent.getTextRange().getEndOffset() - 3);
    }
  }
}