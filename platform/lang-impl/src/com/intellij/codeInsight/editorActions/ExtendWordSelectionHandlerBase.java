/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.text.CharArrayUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class ExtendWordSelectionHandlerBase implements ExtendWordSelectionHandler {
  public abstract boolean canSelect(PsiElement e);

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {

    final TextRange originalRange = e.getTextRange();
    List<TextRange> ranges = expandToWholeLine(editorText, originalRange, true);

    if (ranges.size() == 1 && ranges.contains(originalRange)) {
      ranges = expandToWholeLine(editorText, originalRange, false);
    }

    List<TextRange> result = Lists.newArrayList();
    result.addAll(ranges);
    return result;
  }

  public static List<TextRange> expandToWholeLine(CharSequence text, TextRange range, boolean isSymmetric) {
    int textLength = text.length();
    List<TextRange> result = new ArrayList<TextRange>();

    if (range == null) {
      return result;
    }

    boolean hasNewLines = false;

    for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
      char c = text.charAt(i);

      if (c == '\r' || c == '\n') {
        hasNewLines = true;
        break;
      }
    }

    if (!hasNewLines) {
      result.add(range);
    }


    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    int index1 = CharArrayUtil.shiftBackward(text, startOffset - 1, " \t");
    if (endOffset > startOffset && text.charAt(endOffset - 1) == '\n' || text.charAt(endOffset - 1) == '\r') {
      endOffset--;
    }
    int index2 = Math.min(textLength, CharArrayUtil.shiftForward(text, endOffset, " \t"));

    if (index1 < 0
        || text.charAt(index1) == '\n'
        || text.charAt(index1) == '\r'
        || index2 == textLength
        || text.charAt(index2) == '\n'
        || text.charAt(index2) == '\r') {

      if (!isSymmetric) {
        if (index1 < 0 || text.charAt(index1) == '\n' || text.charAt(index1) == '\r') {
          startOffset = index1 + 1;
        }

        if (index2 == textLength || text.charAt(index2) == '\n' || text.charAt(index2) == '\r') {
          endOffset = index2;
          if (endOffset < textLength) {
            endOffset++;
            if (endOffset < textLength && text.charAt(endOffset - 1) == '\r' && text.charAt(endOffset) == '\n') {
              endOffset++;
            }
          }
        }

        result.add(new TextRange(startOffset, endOffset));
      }
      else {
        if ((index1 < 0 || text.charAt(index1) == '\n' || text.charAt(index1) == '\r') &&
            (index2 == textLength || text.charAt(index2) == '\n' || text.charAt(index2) == '\r')) {
          startOffset = index1 + 1;
          endOffset = index2;
          if (endOffset < textLength) {
            endOffset++;
            if (endOffset < textLength && text.charAt(endOffset - 1) == '\r' && text.charAt(endOffset) == '\n') {
              endOffset++;
            }
          }
          result.add(new TextRange(startOffset, endOffset));
        }
        else {
          result.add(range);
        }
      }
    }
    else {
      result.add(range);
    }

    return result;
  }

  public static List<TextRange> expandToWholeLine(CharSequence text, TextRange range) {
    return expandToWholeLine(text, range, true);
  }
}