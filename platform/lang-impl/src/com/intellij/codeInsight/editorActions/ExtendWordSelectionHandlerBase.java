// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public abstract class ExtendWordSelectionHandlerBase implements ExtendWordSelectionHandler {
  private static final Logger LOG = Logger.getInstance(ExtendWordSelectionHandlerBase.class);
  @Override
  public abstract boolean canSelect(@NotNull PsiElement e);

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    final TextRange originalRange;
    if (e instanceof PsiWhiteSpace) {
      TextRange whiteSpaceRange = expandToWhiteSpace(e, cursorOffset);
      if (whiteSpaceRange == null) return null;
      originalRange = whiteSpaceRange;
    } else if (e instanceof PsiPlainText) {
      TextRange whiteSpaceRange = expandToWhiteSpace(e, cursorOffset);
      originalRange = whiteSpaceRange == null ? e.getTextRange() : whiteSpaceRange;
    } else {
      originalRange = e.getTextRange();
    }

    if (originalRange.getEndOffset() > editorText.length()) {
      throw new RuntimeExceptionWithAttachments(
        "Invalid element range in " + getClass(),
        "element=" + e + "; range=" + originalRange + "; text length=" + editorText.length() + "; editor=" + editor +
        "; committed=" + PsiDocumentManager.getInstance(e.getProject()).isCommitted(editor.getDocument()),
        new Attachment("editor_text.txt", editorText.toString()),
        new Attachment("psi_text.txt", e.getText()));
    }

    List<TextRange> ranges = expandToWholeLine(editorText, originalRange, true);

    if (ranges.size() == 1 && ranges.contains(originalRange)) {
      return expandToWholeLine(editorText, originalRange, false);
    }

    return ranges;
  }

  /**
   * IDEA-110607
   * @param element psiElement at caret
   * @param cursorOffset current caret offset in editor
   * @return range containing all space/tab characters around the cursor
   *         null if there is no such characters or cursor is not at the psiWhiteSpace
   */
  private static @Nullable TextRange expandToWhiteSpace(@NotNull PsiElement element, int cursorOffset) {
    TextRange elementRange = element.getTextRange();
    if (cursorOffset < elementRange.getStartOffset() || cursorOffset > elementRange.getEndOffset()) return null;

    int startOffset = cursorOffset;
    for (int i = cursorOffset - 1; ; --i) {
      Character charBeforeCursor = SelectWordUtil.getCharAfterCursorInPsiElement(element, i);
      if (charBeforeCursor == null || !SelectWordUtil.isExpandableWhiteSpace(charBeforeCursor)) break;
      startOffset = i;
    }

    int endOffset = cursorOffset;
    for (int i = cursorOffset + 1; ; ++i) {
      Character charAfterCursor = SelectWordUtil.getCharBeforeCursorInPsiElement(element, i);
      if (charAfterCursor == null || !SelectWordUtil.isExpandableWhiteSpace(charAfterCursor)) break;
      endOffset = i;
    }

    if (startOffset == cursorOffset && endOffset == cursorOffset) return null;

    return new TextRange(startOffset, endOffset);
  }

  /**
   * Returns minimal selection length for given element.
   *
   * Sometimes the length of word selection should be bounded below.
   * E.g. it is useful in languages that requires prefixes for variable (php, less, etc.).
   * By default this kind of variables will be selected without prefix: @<selection>variable</selection>,
   * but it make sense to exclude this range from selection list.
   * So if this method returns 9 as a minimal length of selection
   * then first selection range for @variable will be: <selection>@variable</selection>.
   *
   * @param element element at caret
   * @param text text in editor
   * @param cursorOffset current caret offset in editor
   * @return minimal selection length for given element
   */
  public int getMinimalTextRangeLength(@NotNull PsiElement element, @NotNull CharSequence text, int cursorOffset) {
    return 0;
  }

  public static @NotNull List<TextRange> expandToWholeLine(@NotNull CharSequence text, @Nullable TextRange range, boolean isSymmetric) {
    List<TextRange> result = new ArrayList<>();

    if (range == null) {
      return result;
    }

    LOG.assertTrue(range.getEndOffset() <= text.length());
    if (!StringUtil.contains(text, range.getStartOffset(), range.getEndOffset(), '\n')) {
      result.add(range);
    }

    TextRange expanded = getExpandedRange(text, range, isSymmetric);
    if (expanded != null) {
      result.add(expanded);
    } else {
      result.add(range);
    }
    return result;
  }

  private static @Nullable TextRange getExpandedRange(@NotNull CharSequence text, @NotNull TextRange range, boolean isSymmetric) {
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    int index1 = CharArrayUtil.shiftBackward(text, startOffset - 1, " \t");
    if (endOffset > startOffset && text.charAt(endOffset - 1) == '\n') {
      endOffset--;
    }
    int textLength = text.length();
    int index2 = Math.min(textLength, CharArrayUtil.shiftForward(text, endOffset, " \t"));

    if (index1 < 0 || text.charAt(index1) == '\n' || index2 == textLength || text.charAt(index2) == '\n') {
      if (!isSymmetric) {
        if (index1 < 0 || text.charAt(index1) == '\n') {
          startOffset = index1 + 1;
        }

        if (index2 == textLength || text.charAt(index2) == '\n') {
          endOffset = index2;
          if (endOffset < textLength) {
            endOffset++;
          }
        }

        return new TextRange(startOffset, endOffset);
      }

      if ((index1 < 0 || text.charAt(index1) == '\n') &&
          (index2 == textLength || text.charAt(index2) == '\n')) {
        startOffset = index1 + 1;
        endOffset = index2;
        if (endOffset < textLength) {
          endOffset++;
        }
        return new TextRange(startOffset, endOffset);
      }
    }

    return null;
  }

  public static @NotNull List<TextRange> expandToWholeLinesWithBlanks(@NotNull CharSequence text, @NotNull TextRange range) {
    List<TextRange> result = new ArrayList<>();
    result.addAll(expandToWholeLine(text, range, true));

    TextRange last = result.isEmpty() ? range : result.get(result.size() - 1);
    int start = last.getStartOffset();
    int end = last.getEndOffset();
    while (true) {
      int blankLineEnd = CharArrayUtil.shiftForward(text, end, " \t");
      if (blankLineEnd >= text.length() || text.charAt(blankLineEnd) != '\n') {
        break;
      }
      end = blankLineEnd + 1;
    }
    if (end == last.getEndOffset()) {
      while (start > 0 && text.charAt(start - 1) == '\n') {
        int blankLineStart = CharArrayUtil.shiftBackward(text, start - 2, " \t");
        if (blankLineStart <= 0 || text.charAt(blankLineStart) != '\n') {
          break;
        }
        start = blankLineStart + 1;
      }
    }
    if (start != last.getStartOffset() || end != last.getEndOffset()) {
      result.add(new TextRange(start, end));
    }

    return result;
  }

  public static @NotNull List<TextRange> expandToWholeLine(@NotNull CharSequence text, @Nullable TextRange range) {
    return expandToWholeLine(text, range, true);
  }
}