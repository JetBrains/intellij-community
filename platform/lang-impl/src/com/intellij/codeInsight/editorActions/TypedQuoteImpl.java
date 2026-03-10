// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageQuoteHandling;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


final class TypedQuoteImpl {
  private static final Logger LOG = Logger.getInstance(TypedQuoteImpl.class);
  private static final KeyedExtensionCollector<QuoteHandler, String> QUOTE_HANDLERS = new KeyedExtensionCollector<>(QuoteHandlerEP.EP_NAME);

  static void registerQuoteHandler(@NotNull FileType fileType, @NotNull QuoteHandler quoteHandler) {
    QUOTE_HANDLERS.addExplicitExtension(fileType.getName(), quoteHandler);
  }

  static @Nullable QuoteHandler getQuoteHandler(@NotNull PsiFile file, @NotNull Editor editor) {
    FileType fileType = TypedCharImpl.getFileType(file, editor);
    QuoteHandler quoteHandler = getQuoteHandlerForType(fileType);
    if (quoteHandler == null) {
      FileType fileFileType = file.getFileType();
      if (fileFileType != fileType) {
        quoteHandler = getQuoteHandlerForType(fileFileType);
      }
    }
    if (quoteHandler == null) {
      return getLanguageQuoteHandler(file.getViewProvider().getBaseLanguage());
    }
    return quoteHandler;
  }

  static boolean beforeQuoteTyped(
    @NotNull Project project,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    char charTyped
  ) {
    if ('"' == charTyped || '\'' == charTyped || '`' == charTyped) {
      if (handleQuote(project, file, editor, charTyped)) {
        return true;
      }
    }
    return false;
  }

  static boolean handleQuote(
    @NotNull Project project,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    char quote
  ) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) {
      return false;
    }
    QuoteHandler quoteHandler = getQuoteHandler(file, editor);
    if (quoteHandler == null) {
      return false;
    }
    int offset = editor.getCaretModel().getOffset();
    Document document = editor.getUiDocument();
    CharSequence chars = document.getCharsSequence();
    int length = document.getTextLength();
    if (isTypingEscapeQuote(editor, quoteHandler, offset)) {
      return false;
    }
    if (offset < length && chars.charAt(offset) == quote) {
      if (isClosingQuote(editor, quoteHandler, offset)) {
        EditorModificationUtil.moveCaretRelatively(editor, 1);
        return true;
      }
    }
    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
    if (!iterator.atEnd()) {
      IElementType tokenType = iterator.getTokenType();
      if (quoteHandler instanceof JavaLikeQuoteHandler javaLike) {
        try {
          if (!javaLike.isAppropriateElementTypeForLiteral(tokenType)) {
            return false;
          }
        } catch (AbstractMethodError incompatiblePluginErrorThatDoesNotInterestUs) {
          // ignore
        }
      }
    }
    TypedCharImpl.typeChar(editor, file.getProject(), quote);
    offset = editor.getCaretModel().getOffset();
    if (quoteHandler instanceof MultiCharQuoteHandler multiChar) {
      CharSequence closingQuote = getClosingQuote(editor, multiChar, offset);
      if (closingQuote != null && hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
        if (offset == document.getTextLength() ||
            !Character.isUnicodeIdentifierPart(document.getCharsSequence().charAt(offset))) { //any better heuristic or an API?
          boolean handled = TypedDelegateImpl.fireBeforeClosingQuoteInserted(project, file, editor, quote, closingQuote);
          if (!handled) {
            multiChar.insertClosingQuote(editor, offset, file, closingQuote);
          }
          return true;
        }
      }
    }
    if (offset > 0 &&
        isOpeningQuote(editor, quoteHandler, offset - 1) &&
        hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
      if (offset == document.getTextLength() ||
          !Character.isUnicodeIdentifierPart(document.getCharsSequence().charAt(offset))) { //any better heuristic or an API?
        String quoteString = String.valueOf(quote);
        boolean handled = TypedDelegateImpl.fireBeforeClosingQuoteInserted(project, file, editor, quote, quoteString);
        if (!handled) {
          document.insertString(offset, quoteString);
          TabOutScopesTracker.getInstance().registerEmptyScope(editor, offset);
        }
      }
    }
    return true;
  }

  private static boolean isTypingEscapeQuote(
    @NotNull Editor editor,
    @NotNull QuoteHandler quoteHandler,
    int offset
  ) {
    if (offset == 0) {
      return false;
    }
    CharSequence chars = editor.getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftBackward(chars, offset - 1, "\\");
    int slashCount = offset - 1 - offset1;
    return slashCount % 2 != 0 && isInsideLiteral(editor, quoteHandler, offset);
  }

  private static boolean isInsideLiteral(
    @NotNull Editor editor,
    @NotNull QuoteHandler quoteHandler,
    int offset
  ) {
    if (offset == 0) {
      return false;
    }
    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset - 1);
    if (iterator == null) {
      return false;
    }
    return quoteHandler.isInsideLiteral(iterator);
  }

  private static boolean isClosingQuote(
    @NotNull Editor editor,
    @NotNull QuoteHandler quoteHandler,
    int offset
  ) {
    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset);
    if (iterator == null) {
      return false;
    }
    return quoteHandler.isClosingQuote(iterator, offset);
  }

  private static @Nullable CharSequence getClosingQuote(
    @NotNull Editor editor,
    @NotNull MultiCharQuoteHandler quoteHandler,
    int offset
  ) {
    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset);
    if (iterator == null) {
      return null;
    }
    return quoteHandler.getClosingQuote(iterator, offset);
  }

  private static boolean isOpeningQuote(
    @NotNull Editor editor,
    @NotNull QuoteHandler quoteHandler,
    int offset
  ) {
    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset);
    if (iterator == null) {
      return false;
    }
    return quoteHandler.isOpeningQuote(iterator, offset);
  }

  private static boolean hasNonClosedLiterals(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset);
    if (iterator == null) {
      return false;
    }
    return quoteHandler.hasNonClosedLiteral(editor, iterator, offset);
  }

  private static @Nullable HighlighterIterator createIteratorAndCheckNotAtEnd(@NotNull Editor editor, int offset) {
    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
    if (iterator.atEnd()) {
      LOG.error("Iterator " + iterator + " ended unexpectedly right after creation");
      return null;
    }
    return iterator;
  }

  private static QuoteHandler getLanguageQuoteHandler(Language baseLanguage) {
    return LanguageQuoteHandling.INSTANCE.forLanguage(baseLanguage);
  }

  private static QuoteHandler getQuoteHandlerForType(@NotNull FileType fileType) {
    return ContainerUtil.getFirstItem(QUOTE_HANDLERS.forKey(fileType.getName()));
  }
}
