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

    if (charAtOffset == '.' || charAtOffset == ' ') {
      if (isInsideLinkTag(document, offset)) {
        return false;
      }
    }

    return true;
  }

  private static boolean isInsideLinkTag(Document document, int offset) {
    int lineNumber = document.getLineNumber(offset);
    int lineStartOffset = document.getLineStartOffset(lineNumber);
    int lineEndOffset = document.getLineEndOffset(lineNumber);

    CharSequence sequence = document.getCharsSequence();

    return CharArrayUtil.indexOf(sequence, "{@link", lineStartOffset, offset) > 0
      && CharArrayUtil.indexOf(sequence, "}", offset, lineEndOffset) > 0;
  }
}
