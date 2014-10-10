/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LineIndentInfoBuilder {
  private static final int MAX_LINES_TO_PROCESS = 500;

  private final CharSequence myText;
  private final int myLength;

  public LineIndentInfoBuilder(@NotNull CharSequence text) {
    myText = text;
    myLength = text.length();
  }

  @NotNull
  public List<LineIndentInfo> build() {
    List<LineIndentInfo> lineIndentInfos = ContainerUtil.newArrayList();

    int lineStartOffset = 0;
    int currentLine = 0;

    while (lineStartOffset < myText.length() && currentLine < MAX_LINES_TO_PROCESS) {
      int lineEndOffset = getLineEndOffset(lineStartOffset);
      int textStartOffset = CharArrayUtil.shiftForward(myText, lineStartOffset, lineEndOffset, " \t");

      if (textStartOffset != lineEndOffset) {
        lineIndentInfos.add(createInfoFromWhiteSpaceRange(lineStartOffset, textStartOffset));
      } else {
        lineIndentInfos.add(LineIndentInfo.EMPTY_LINE);
      }

      lineStartOffset = lineEndOffset + 1;
      currentLine++;
    }

    return lineIndentInfos;
  }

  @NotNull
  private LineIndentInfo createInfoFromWhiteSpaceRange(int lineStartOffset, int textStartOffset) {
    if (myText.charAt(textStartOffset) == '*') {
      return LineIndentInfo.LINE_WITH_COMMENT;
    }
    else if (CharArrayUtil.indexOf(myText, "\t", lineStartOffset, textStartOffset) > 0) {
      return LineIndentInfo.LINE_WITH_TABS;
    }
    else {
      int indentSize = textStartOffset - lineStartOffset;
      return LineIndentInfo.newWhiteSpaceIndent(indentSize);
    }
  }

  private int getLineEndOffset(int lineStartOffset) {
    int lineEndOffset = CharArrayUtil.indexOf(myText, "\n", lineStartOffset, myLength);
    if (lineEndOffset < 0) {
      lineEndOffset = myText.length();
    }
    return lineEndOffset;
  }
}
