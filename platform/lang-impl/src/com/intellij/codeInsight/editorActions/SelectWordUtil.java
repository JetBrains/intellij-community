// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public final class SelectWordUtil {
  private SelectWordUtil() {
  }

  public static final CharCondition JAVA_IDENTIFIER_PART_CONDITION = ch -> Character.isJavaIdentifierPart(ch);

  public static void addWordSelection(boolean camel, CharSequence editorText, int cursorOffset, @NotNull List<? super TextRange> ranges) {
    addWordSelection(camel, editorText, cursorOffset, ranges, JAVA_IDENTIFIER_PART_CONDITION);
  }

  public static void addWordOrLexemeSelection(boolean camel, @NotNull Editor editor, int cursorOffset, @NotNull List<? super TextRange> ranges) {
    addWordOrLexemeSelection(camel, editor, cursorOffset, ranges, JAVA_IDENTIFIER_PART_CONDITION);
  }

  public static void addWordSelection(boolean camel,
                                      CharSequence editorText,
                                      int cursorOffset,
                                      @NotNull List<? super TextRange> ranges,
                                      CharCondition isWordPartCondition) {
    TextRange camelRange = camel ? getCamelSelectionRange(editorText, cursorOffset, isWordPartCondition) : null;
    if (camelRange != null) {
      ranges.add(camelRange);
    }

    TextRange range = getWordSelectionRange(editorText, cursorOffset, isWordPartCondition);
    if (range != null && !range.equals(camelRange)) {
      ranges.add(range);
    }
  }

  public static void addWordOrLexemeSelection(boolean camel,
                                              @NotNull Editor editor,
                                              int cursorOffset,
                                              @NotNull List<? super TextRange> ranges,
                                              CharCondition isWordPartCondition) {
    TextRange camelRange = camel ? getCamelSelectionRange(editor.getDocument().getImmutableCharSequence(),
                                                          cursorOffset, isWordPartCondition) : null;
    if (camelRange != null) {
      ranges.add(camelRange);
    }

    TextRange range = getWordOrLexemeSelectionRange(editor, cursorOffset, isWordPartCondition);
    if (range != null && !range.equals(camelRange)) {
      ranges.add(range);
    }
  }

  private static @Nullable TextRange getCamelSelectionRange(CharSequence editorText, int cursorOffset, CharCondition isWordPartCondition) {
    if (cursorOffset < 0 || cursorOffset >= editorText.length()) {
      return null;
    }
    if (cursorOffset > 0 && !isWordPartCondition.value(editorText.charAt(cursorOffset)) &&
        isWordPartCondition.value(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (isWordPartCondition.value(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset + 1;
      final int textLen = editorText.length();

      while (start > 0 && isWordPartCondition.value(editorText.charAt(start - 1)) && !EditorActionUtil.isHumpBound(editorText, start, true)) {
        start--;
      }

      while (end < textLen && isWordPartCondition.value(editorText.charAt(end)) && !EditorActionUtil.isHumpBound(editorText, end, false)) {
        end++;
      }

      if (start + 1 < end) {
        return new TextRange(start, end);
      }
    }

    return null;
  }

  public static @Nullable TextRange getWordOrLexemeSelectionRange(@NotNull Editor editor, int cursorOffset,
                                                                  @NotNull CharCondition isWordPartCondition) {
    return getWordOrLexemeSelectionRange(editor, editor.getDocument().getImmutableCharSequence(), cursorOffset, isWordPartCondition);
  }

  public static @Nullable TextRange getWordSelectionRange(@NotNull CharSequence editorText, int cursorOffset,
                                                          @NotNull CharCondition isWordPartCondition) {
    return getWordOrLexemeSelectionRange(null, editorText, cursorOffset, isWordPartCondition);
  }

  private static @Nullable TextRange getWordOrLexemeSelectionRange(@Nullable Editor editor, @NotNull CharSequence editorText, int cursorOffset,
                                                                   @NotNull CharCondition isWordPartCondition) {
    int length = editorText.length();
    if (length == 0) return null;
    if (cursorOffset == length ||
        cursorOffset > 0 && !isWordPartCondition.value(editorText.charAt(cursorOffset)) &&
        isWordPartCondition.value(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (isWordPartCondition.value(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset;

      while (start > 0 && isWordPartCondition.value(editorText.charAt(start - 1)) &&
             (editor == null || !EditorActionUtil.isLexemeBoundary(editor, start))) {
        start--;
      }

      while (end < length && isWordPartCondition.value(editorText.charAt(end)) &&
             (end == start || editor == null || !EditorActionUtil.isLexemeBoundary(editor, end))) {
        end++;
      }

      return new TextRange(start, end);
    }

    return null;
  }

  public static void processRanges(@Nullable PsiElement element,
                                   @NotNull CharSequence text,
                                   int cursorOffset,
                                   @NotNull Editor editor,
                                   @NotNull Processor<? super TextRange> consumer) {
    if (element == null) return;

    PsiFile file = element.getContainingFile();

    FileViewProvider viewProvider = file.getViewProvider();

    processInFile(element, consumer, text, cursorOffset, editor);

    for (PsiFile psiFile : viewProvider.getAllFiles()) {
      if (psiFile == file) continue;

      FileASTNode fileNode = psiFile.getNode();
      if (fileNode == null) continue;

      ASTNode nodeAt = fileNode.findLeafElementAt(element.getTextOffset());
      if (nodeAt == null) continue;

      PsiElement elementAt = nodeAt.getPsi();

      while (!(elementAt instanceof PsiFile) && elementAt != null) {
        if (elementAt.getTextRange().contains(element.getTextRange())) break;

        elementAt = elementAt.getParent();
      }

      if (elementAt == null) continue;

      processInFile(elementAt, consumer, text, cursorOffset, editor);
    }
  }

  private static void processInFile(final @NotNull PsiElement element,
                                    @NotNull Processor<? super TextRange> consumer,
                                    @NotNull CharSequence text,
                                    final int cursorOffset,
                                    @NotNull Editor editor) {
    DumbService.getInstance(element.getProject()).withAlternativeResolveEnabled(() -> {
      PsiElement e = element;
      while (e != null && !(e instanceof PsiFile)) {
        if (processElement(e, consumer, text, cursorOffset, editor)) return;
        e = e.getParent();
      }
    });
  }

  private static boolean processElement(@NotNull PsiElement element,
                                        @NotNull Processor<? super TextRange> processor,
                                        @NotNull CharSequence text,
                                        int cursorOffset,
                                        @NotNull Editor editor) {
    ExtendWordSelectionHandler[] extendWordSelectionHandlers = ExtendWordSelectionHandler.EP_NAME.getExtensions();
    int minimalTextRangeLength = 0;
    List<ExtendWordSelectionHandler> availableSelectioners = new LinkedList<>();
    for (ExtendWordSelectionHandler selectioner : extendWordSelectionHandlers) {
      if (selectioner.canSelect(element)) {
        int selectionerMinimalTextRange = selectioner instanceof ExtendWordSelectionHandlerBase
          ? ((ExtendWordSelectionHandlerBase)selectioner).getMinimalTextRangeLength(element, text, cursorOffset)
          : 0;
        minimalTextRangeLength = Math.max(minimalTextRangeLength, selectionerMinimalTextRange);
        availableSelectioners.add(selectioner);
      }
    }
    boolean stop = false;
    for (ExtendWordSelectionHandler selectioner : availableSelectioners) {
      List<TextRange> ranges = askSelectioner(element, text, cursorOffset, editor, selectioner);
      if (ranges == null) continue;

      for (TextRange range : ranges) {
        if (range == null || range.getLength() < minimalTextRangeLength) continue;

        stop |= processor.process(range);
      }
    }

    return stop;
  }

  private static @Nullable List<TextRange> askSelectioner(@NotNull PsiElement element,
                                                          @NotNull CharSequence text,
                                                          int cursorOffset,
                                                          @NotNull Editor editor,
                                                          @NotNull ExtendWordSelectionHandler selectioner) {
    try {
      long stamp = editor.getDocument().getModificationStamp();
      List<TextRange> ranges = selectioner.select(element, text, cursorOffset, editor);
      if (stamp != editor.getDocument().getModificationStamp()) {
        throw new AssertionError("Selectioner " + selectioner + " has changed the document");
      }
      return ranges;
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  public static void addWordHonoringEscapeSequences(CharSequence editorText,
                                                    TextRange literalTextRange,
                                                    int cursorOffset,
                                                    Lexer lexer,
                                                    List<? super TextRange> result) {
    lexer.start(editorText, literalTextRange.getStartOffset(), literalTextRange.getEndOffset());

    while (lexer.getTokenType() != null) {
      if (lexer.getTokenStart() <= cursorOffset && cursorOffset < lexer.getTokenEnd()) {
        if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType())) {
          result.add(new TextRange(lexer.getTokenStart(), lexer.getTokenEnd()));
        }
        else {
          TextRange word = getWordSelectionRange(editorText, cursorOffset, JAVA_IDENTIFIER_PART_CONDITION);
          if (word != null) {
            result.add(new TextRange(Math.max(word.getStartOffset(), lexer.getTokenStart()),
                                     Math.min(word.getEndOffset(), lexer.getTokenEnd())));
          }
        }
        break;
      }
      lexer.advance();
    }
  }

  /**
   * Returns if there is any expandable whitespace belonging to the given psiWhiteSpace
   * by any side of a caret at specified cursorPosition
   */
  public static boolean canWhiteSpaceBeExpanded(@NotNull PsiWhiteSpace psiWhiteSpace, int cursorPosition, @Nullable Caret caret, @NotNull Editor editor) {
    if (!AdvancedSettings.getBoolean("editor.selection.expand-whitespaces")) return false;
    
    TextRange selectionRange = caret != null && caret.hasSelection() ? caret.getSelectionRange() : null;
    if (selectionRange != null && !psiWhiteSpace.getTextRange().contains(selectionRange)) return false;
    
    int startOffset = selectionRange == null ? cursorPosition : selectionRange.getStartOffset();
    Character charBeforeStartOffset = getCharBeforeCursorInPsiElement(psiWhiteSpace, startOffset);
    if (charBeforeStartOffset != null && isExpandableWhiteSpace(charBeforeStartOffset)) return true;

    int endOffset = selectionRange == null ? cursorPosition : selectionRange.getEndOffset();
    Character charBeforeEndOffset = getCharBeforeCursorInPsiElement(psiWhiteSpace, endOffset);
    Character charAfterEndOffset = getCharAfterCursorInPsiElement(psiWhiteSpace, endOffset);
    if (charAfterEndOffset != null && isExpandableWhiteSpace(charAfterEndOffset)) return true;
    if (charBeforeEndOffset != null && isExpandableWhiteSpace(charBeforeEndOffset) 
        && charAfterEndOffset != null && Character.isWhitespace(charAfterEndOffset)) {
      return true;
    }

    return false;
  }

  /**
   * Gets character in psiElement before specified caret position
   * @param psiElement element at caret
   * @param cursorPosition caret offset
   * @return char before caret
   *         null if char is outside the psiElement
   */
  public static @Nullable Character getCharBeforeCursorInPsiElement(PsiElement psiElement, int cursorPosition) {
    TextRange elementRange = psiElement.getTextRange();
    int index = cursorPosition - elementRange.getStartOffset() - 1;
    String elementText = psiElement.getText();
    if (index < 0 || index >= elementText.length()) return null;
    return elementText.charAt(index);
  }

  /**
   * Gets character in psiElement after (at) specified caret position
   * @param psiElement element at caret
   * @param cursorPosition caret offset
   * @return char before caret
   *         null if char is outside the psiElement
   */
  public static @Nullable Character getCharAfterCursorInPsiElement(PsiElement psiElement, int cursorPosition) {
    return getCharBeforeCursorInPsiElement(psiElement, cursorPosition + 1);
  }

  // IDEA-110607
  // whitespace characters that should be selected as one word on double-click
  public static boolean isExpandableWhiteSpace(char ch) {
    return ch == ' ' || ch == '\t';
  }

  @FunctionalInterface
  public interface CharCondition { boolean value(char ch); }
}
