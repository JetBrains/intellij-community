/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.editor.DefaultLineWrapPositionStrategy;
import com.intellij.openapi.editor.Document;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class JavaLineWrapPositionStrategy extends DefaultLineWrapPositionStrategy {

  @Override
  protected boolean canUseOffset(@NotNull Document document, int offset, boolean virtual) {
    CharSequence chars = document.getCharsSequence();
    char charAtOffset = chars.charAt(offset);

    if (charAtOffset == '.') {
      if (offset > 0 && chars.charAt(offset - 1) == '.' || offset + 1 < chars.length() && chars.charAt(offset + 1) == '.') {
        return false;
      }
    }

    if (isInsideJDLinkTag(document, offset) || isInsideHrefTag(document, offset)) {
      return false;
    }

    return true;
  }

  private static boolean isInsideHrefTag(Document document, int offset) {
    return isInsideTag(document, offset, "<a", ">");
  }

  private static boolean isInsideJDLinkTag(Document document, int offset) {
    return isInsideTag(document, offset, "{@link", "}");
  }

  private static boolean isInsideTag(Document document, int offset, String tagStart, String tagEnd) {
    CharSequence sequence = document.getCharsSequence();

    final int lineNumber = document.getLineNumber(offset);
    final int lineStartOffset = document.getLineStartOffset(lineNumber);
    final int lineEndOffset = document.getLineEndOffset(lineNumber);

    int searchStartOffset = lineStartOffset;
    int searchEndOffset = lineEndOffset;

    if (lineEndOffset - lineStartOffset > 200) {
      searchStartOffset = Math.max(offset - 100, lineStartOffset);
      searchEndOffset = Math.min(offset + 100, lineEndOffset);
    }

    CharSequence textChunkAroundOffset = sequence.subSequence(searchStartOffset, searchEndOffset);
    int offsetInChunk = offset - searchStartOffset;
    
    int tagStartIndex = CharArrayUtil.lastIndexOf(textChunkAroundOffset, tagStart, offsetInChunk);
    if (tagStartIndex > 0) {
      int nearestTagEndIndex = CharArrayUtil.indexOf(textChunkAroundOffset, tagEnd, tagStartIndex);
      return nearestTagEndIndex > offsetInChunk;
    }
    return false;
  }
}