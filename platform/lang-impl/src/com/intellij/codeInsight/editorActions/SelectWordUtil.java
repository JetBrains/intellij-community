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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mike
 */
public class SelectWordUtil {
    
  private static ExtendWordSelectionHandler[] SELECTIONERS = new ExtendWordSelectionHandler[]{
  };

  private static boolean ourExtensionsLoaded = false;

  private SelectWordUtil() {
  }

  public static void registerSelectioner(ExtendWordSelectionHandler selectioner) {
    SELECTIONERS = ArrayUtil.append(SELECTIONERS, selectioner);
  }

  static ExtendWordSelectionHandler[] getExtendWordSelectionHandlers() {
    if (!ourExtensionsLoaded) {
      ourExtensionsLoaded = true;
      for (ExtendWordSelectionHandler handler : Extensions.getExtensions(ExtendWordSelectionHandler.EP_NAME)) {
        registerSelectioner(handler);        
      }
    }
    return SELECTIONERS;
  }

  public static void addWordSelection(boolean camel, CharSequence editorText, int cursorOffset, @NotNull List<TextRange> ranges) {
    TextRange camelRange = camel ? getCamelSelectionRange(editorText, cursorOffset) : null;
    if (camelRange != null) {
      ranges.add(camelRange);
    }

    TextRange range = getWordSelectionRange(editorText, cursorOffset);
    if (range != null && !range.equals(camelRange)) {
      ranges.add(range);
    }
  }

  @Nullable
  private static TextRange getCamelSelectionRange(CharSequence editorText, int cursorOffset) {
    if (cursorOffset < 0 || cursorOffset >= editorText.length()) {
      return null;
    }
    if (cursorOffset > 0 && !Character.isJavaIdentifierPart(editorText.charAt(cursorOffset)) &&
        Character.isJavaIdentifierPart(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (Character.isJavaIdentifierPart(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset + 1;
      final int textLen = editorText.length();

      while (start > 0 && Character.isJavaIdentifierPart(editorText.charAt(start - 1))) {
        final char prevChar = editorText.charAt(start - 1);
        final char curChar = editorText.charAt(start);
        final char nextChar = start + 1 < textLen ? editorText.charAt(start + 1) : 0; // 0x00 is not lowercase.

        if (Character.isLowerCase(prevChar) && Character.isUpperCase(curChar) || prevChar == '_' && curChar != '_' ||
            Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar)) {
          break;
        }
        start--;
      }

      while (end < textLen && Character.isJavaIdentifierPart(editorText.charAt(end))) {
        final char prevChar = editorText.charAt(end - 1);
        final char curChar = editorText.charAt(end);
        final char nextChar = end + 1 < textLen ? editorText.charAt(end + 1) : 0; // 0x00 is not lowercase

        if (Character.isLowerCase(prevChar) && Character.isUpperCase(curChar) || prevChar != '_' && curChar == '_' ||
            Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar)) {
          break;
        }
        end++;
      }

      if (start + 1 < end) {
        return new TextRange(start, end);
      }
    }

    return null;
  }

  @Nullable
  public static TextRange getWordSelectionRange(@NotNull CharSequence editorText, int cursorOffset) {
    int length = editorText.length();
    if (length == 0) return null;
    if (cursorOffset == length ||
        cursorOffset > 0 && !Character.isJavaIdentifierPart(editorText.charAt(cursorOffset)) &&
        Character.isJavaIdentifierPart(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (Character.isJavaIdentifierPart(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset;

      while (start > 0 && Character.isJavaIdentifierPart(editorText.charAt(start - 1))) {
        start--;
      }

      while (end < length && Character.isJavaIdentifierPart(editorText.charAt(end))) {
        end++;
      }

      return new TextRange(start, end);
    }

    return null;
  }

}
