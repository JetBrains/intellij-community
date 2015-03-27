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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class ContinuationIndentDetector {
  private List<Bracket> myBrackets = ContainerUtil.newArrayList();

  private final CharSequence myText;
  private final int myLength;
  private boolean myStackCorrupted = false;

  public ContinuationIndentDetector(@NotNull CharSequence text) {
    myText = text;
    myLength = text.length();
  }

  public void feedLineStartingAt(int startOffset) {
    if (myStackCorrupted) return;

    int lineEndOffset = getLineEndOffset(startOffset);

    for (int i = startOffset; i < lineEndOffset; i++) {
      char c = myText.charAt(i);
      Bracket bracket = Bracket.forChar(c);
      if (bracket == null) continue;

      if (bracket.isOpening()) {
        myBrackets.add(bracket);
      }
      else {
        if (myBrackets.isEmpty()) {
          myStackCorrupted = true;
        }
        myBrackets.remove(myBrackets.size() - 1);
      }
    }
  }

  public boolean isContinuationIndent(int lineStartOffset) {
    if (myStackCorrupted || myBrackets.isEmpty()) {
      return false;
    }
    int textStartOffset = CharArrayUtil.shiftForward(myText, lineStartOffset, " \t");

    for (int i = myBrackets.size() - 1; i >= 0; i--) {
      if (myBrackets.get(i) == Bracket.LPARENTH && myText.charAt(textStartOffset) != ')') return true;
    }

    return false;
  }

  private int getLineEndOffset(int lineStartOffset) {
    int lineEndOffset = CharArrayUtil.indexOf(myText, "\n", lineStartOffset, myLength);
    return lineEndOffset < 0 ? myLength : lineEndOffset;
  }

  private enum Bracket {
    LBRACE('{', true),
    LPARENTH('(', true),
    RBRACE('}', false),
    RPARENTH(')', false);

    private final char myChar;
    private boolean myIsOpeningBracket;

    Bracket(char c, boolean isOpeningBracket) {
      myIsOpeningBracket = isOpeningBracket;
      myChar = c;
    }

    boolean isOpening() {
      return myIsOpeningBracket;
    }

    static Bracket forChar(char c) {
      for (Bracket bracket : Bracket.values()) {
        if (bracket.myChar == c) return bracket;
      }
      return null;
    }
  }
}
