// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.codeInsight.highlighting.NontrivialBraceMatcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DefaultRawTypedHandler;
import com.intellij.openapi.editor.impl.TypedActionImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;


final class TypedParenImpl {
  private static final Logger LOG = Logger.getInstance(TypedParenImpl.class);

  static void afterParenTyped(
    @NotNull Project project,
    @NotNull FileType fileType,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    char charTyped
  ) {
    if (('(' == charTyped || '[' == charTyped || '{' == charTyped) &&
        autoInsertBracket() &&
        fileType != FileTypes.PLAIN_TEXT) {
      afterLParenTyped(project, fileType, file, editor, charTyped);
    } else if ('}' == charTyped) {
      indentClosingBrace(project, editor);
    } else if (')' == charTyped) {
      indentClosingParenth(project, editor);
    }
  }

  static boolean beforeParenTyped(@NotNull FileType fileType, @NotNull Editor editor, char charTyped) {
    if (')' == charTyped || ']' == charTyped || '}' == charTyped) {
      if (FileTypes.PLAIN_TEXT != fileType) {
        if (beforeRParenTyped(fileType, editor, charTyped)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean beforeRParenTyped(
    @NotNull FileType fileType,
    @NotNull Editor editor,
    char charTyped
  ) {
    if (!autoInsertBracket()) {
      return false;
    }
    Document document = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    if (offset == document.getTextLength()) {
      return false;
    }
    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
    if (iterator.atEnd()) {
      return false;
    }
    if (iterator.getEnd() - iterator.getStart() != 1 ||
        document.getCharsSequence().charAt(iterator.getStart()) != charTyped) {
      return false;
    }
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
    CharSequence text = document.getCharsSequence();
    if (!braceMatcher.isRBraceToken(iterator, text, fileType)) {
      return false;
    }
    IElementType tokenType = iterator.getTokenType();
    iterator.retreat();
    IElementType lparenTokenType = braceMatcher.getOppositeBraceTokenType(tokenType);
    int lparenthOffset = BraceMatchingUtil.findLeftmostLParen(iterator, lparenTokenType, text, fileType);
    if (lparenthOffset < 0) {
      if (braceMatcher instanceof NontrivialBraceMatcher) {
        for (IElementType t : ((NontrivialBraceMatcher)braceMatcher).getOppositeBraceTokenTypes(tokenType)) {
          if (t == lparenTokenType) continue;
          lparenthOffset = BraceMatchingUtil.findLeftmostLParen(
            iterator,
            t,
            text,
            fileType
          );
          if (lparenthOffset >= 0) {
            break;
          }
        }
      }
      if (lparenthOffset < 0) {
        return false;
      }
    }
    iterator = editor.getHighlighter().createIterator(lparenthOffset);
    boolean matched = BraceMatchingUtil.matchBrace(text, fileType, iterator, true, true);
    if (!matched) {
      return false;
    }
    EditorModificationUtil.moveCaretRelatively(editor, 1);
    return true;
  }

  static void indentOpenedParen(@NotNull Project project, @NotNull Editor editor, char ch) {
    if ('{' == ch) {
      indentOpenedBrace(project, editor);
    }
    if ('(' == ch) {
      indentOpenedParenth(project, editor);
    }
  }

  static void indentOpenedBrace(@NotNull Project project, @NotNull Editor editor) {
    indentBrace(project, editor, '{');
  }

  static void indentBrace(@NotNull Project project, @NotNull Editor editor, char braceChar) {
    int offset = editor.getCaretModel().getOffset() - 1;
    Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    if (offset < 0 || chars.charAt(offset) != braceChar) {
      return;
    }
    int spaceStart = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (spaceStart < 0 || chars.charAt(spaceStart) == '\n' || chars.charAt(spaceStart) == '\r') {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null || !file.isWritable()) {
        return;
      }
      EditorHighlighter highlighter = editor.getHighlighter();
      HighlighterIterator iterator = highlighter.createIterator(offset);
      FileType fileType = file.getFileType();
      BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
      boolean rBraceToken = braceMatcher.isRBraceToken(iterator, chars, fileType);
      boolean isBrace = braceMatcher.isLBraceToken(iterator, chars, fileType) || rBraceToken;
      int lBraceOffset = -1;
      if (CodeInsightSettings.getInstance().REFORMAT_BLOCK_ON_RBRACE &&
          rBraceToken &&
          braceMatcher.isStructuralBrace(iterator, chars, fileType) && offset > 0) {
        lBraceOffset = BraceMatchingUtil.findLeftLParen(
          highlighter.createIterator(offset - 1),
          braceMatcher.getOppositeBraceTokenType(iterator.getTokenType()),
          editor.getDocument().getCharsSequence(),
          fileType
        );
      }
      if (isBrace) {
        DefaultRawTypedHandler handler = ((TypedActionImpl)TypedAction.getInstance()).getDefaultRawTypedHandler();
        handler.beginUndoablePostProcessing();
        int finalLBraceOffset = lBraceOffset;
        EditorThreading.write(() -> {
          TypingActionsExtension extension = TypingActionsExtension.findForContext(project, editor);
          try {
            RangeMarker marker = document.createRangeMarker(offset, offset + 1);
            if (finalLBraceOffset != -1) {
              extension.format(
                project,
                editor,
                CodeInsightSettings.REFORMAT_BLOCK,
                finalLBraceOffset,
                offset,
                0,
                false,
                false
              );
            } else {
              extension.format(
                project,
                editor,
                CodeInsightSettings.INDENT_EACH_LINE,
                offset,
                offset,
                0,
                false,
                false
              );
            }
            editor.getCaretModel().moveToOffset(marker.getStartOffset() + 1);
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            editor.getSelectionModel().removeSelection();
            marker.dispose();
          } catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        });
      }
    }
  }

  private static void afterLParenTyped(
    @NotNull Project project,
    @NotNull FileType fileType,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    char lparenChar
  ) {
    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
    Document document = editor.getUiDocument();
    boolean atEndOfDocument = offset == document.getTextLength();
    if (!atEndOfDocument) {
      iterator.retreat();
    }
    if (iterator.atEnd()) {
      return;
    }
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
    if (iterator.atEnd()) {
      return;
    }
    IElementType braceTokenType = iterator.getTokenType();
    CharSequence fileText = document.getCharsSequence();
    if (!braceMatcher.isLBraceToken(iterator, fileText, fileType)) {
      return;
    }
    if (!iterator.atEnd()) {
      iterator.advance();
      if (!iterator.atEnd() &&
          !BraceMatchingUtil.isPairedBracesAllowedBeforeTypeInFileType(braceTokenType, iterator.getTokenType(), fileType)) {
        return;
      }
      iterator.retreat();
    }
    int lparenOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceTokenType, fileText, fileType);
    if (lparenOffset < 0) {
      lparenOffset = 0;
    }
    iterator = editor.getHighlighter().createIterator(lparenOffset);
    boolean matched = BraceMatchingUtil.matchBrace(fileText, fileType, iterator, true, true);
    if (!matched) {
      String text = switch (lparenChar) {
        case '(' -> ")";
        case '[' -> "]";
        case '<' -> ">";
        case '{' -> "}";
        default -> throw new AssertionError("Unknown char '" + lparenChar + '\'');
      };
      if (TypedDelegateImpl.fireBeforeClosingParenInserted(project, file, editor, text.charAt(0))) {
        return;
      }
      document.insertString(offset, text);
      TabOutScopesTracker.getInstance().registerEmptyScope(editor, offset);
    }
  }

  private static void indentClosingBrace(@NotNull Project project, @NotNull Editor editor) {
    indentBrace(project, editor, '}');
  }

  private static void indentOpenedParenth(@NotNull Project project, @NotNull Editor editor) {
    indentBrace(project, editor, '(');
  }

  private static void indentClosingParenth(@NotNull Project project, @NotNull Editor editor) {
    indentBrace(project, editor, ')');
  }

  private static boolean autoInsertBracket() {
    return CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
  }
}
