/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.editorActions;

import com.intellij.diagnostic.LogEventException;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public abstract class ExtendWordSelectionHandlerBase implements ExtendWordSelectionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase");
  @Override
  public abstract boolean canSelect(@NotNull PsiElement e);

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    final TextRange originalRange = e.getTextRange();
    if (originalRange.getEndOffset() > editorText.length()) {
      throw new LogEventException("Invalid element range in " + getClass(),
                                  "element=" + e +
                                  "; range=" + originalRange +
                                  "; text length=" + editorText.length() +
                                  "; editor=" + editor +
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

  public static List<TextRange> expandToWholeLine(CharSequence text, @Nullable TextRange range, boolean isSymmetric) {
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

  @Nullable
  private static TextRange getExpandedRange(CharSequence text, TextRange range, boolean isSymmetric) {
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

  public static List<TextRange> expandToWholeLinesWithBlanks(CharSequence text, TextRange range) {
    List<TextRange> result = ContainerUtil.newArrayList();
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

  public static List<TextRange> expandToWholeLine(CharSequence text, TextRange range) {
    return expandToWholeLine(text, range, true);
  }
}