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
import com.intellij.util.containers.Stack;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

class ContinuationIndentDetector {
  private Stack<Bracket> myOpenedBrackets = ContainerUtil.newStack();

  private final CharSequence myText;
  private final int myLength;
  private boolean myIncorrectBracketsOrder = false;

  private Character myOpeningStringLiteralDelimiter = null;

  public ContinuationIndentDetector(@NotNull CharSequence text) {
    myText = text;
    myLength = text.length();
  }

  public void feedLineStartingAt(int startOffset) {
    if (myIncorrectBracketsOrder) return;

    int lineEndOffset = CharArrayUtil.indexOf(myText, "\n", startOffset, myLength);
    if (lineEndOffset < 0) lineEndOffset = myLength;

    for (int i = startOffset; i < lineEndOffset; i++) {
      final char currentChar = myText.charAt(i);

      if (isStringLiteralDelimiter(currentChar)) {
        processStringLiteralDelimiter(currentChar);
      }
      else {
        Bracket bracket = Bracket.forChar(currentChar);
        if (bracket != null && myOpeningStringLiteralDelimiter == null) {
          processBracket(bracket);
        }
      }
    }
  }

  private void processStringLiteralDelimiter(char delimiter) {
    if (myOpeningStringLiteralDelimiter == null) {
      myOpeningStringLiteralDelimiter = delimiter;
    }
    else if (myOpeningStringLiteralDelimiter.charValue() == delimiter) {
      myOpeningStringLiteralDelimiter = null;
    }
  }

  private boolean isStringLiteralDelimiter(char currentChar) {
    return currentChar == '\'' || currentChar == '\"';
  }

  private void processBracket(@NotNull Bracket bracket) {
    if (bracket.isOpening()) {
      myOpenedBrackets.add(bracket);
    }
    else {
      if (!myOpenedBrackets.isEmpty() && bracket.isClosing(myOpenedBrackets.peek())) {
        myOpenedBrackets.pop();
      }
      else {
        myIncorrectBracketsOrder = true;
      }
    }
  }

  public boolean isContinuationIndent() {
    if (myIncorrectBracketsOrder || myOpenedBrackets.isEmpty()) {
      return false;
    }
    return myOpenedBrackets.peek() == Bracket.LPARENTH;
  }


  private enum Bracket {
    LBRACE('{') {
      @Override
      boolean isOpening() {
        return true;
      }
    },
    LPARENTH('(') {
      @Override
      boolean isOpening() {
        return true;
      }
    },
    RBRACE('}') {
      @Override
      public boolean isClosing(Bracket bracket) {
        return bracket == LBRACE;
      }
    },
    RPARENTH(')') {
      @Override
      public boolean isClosing(Bracket bracket) {
        return bracket == LPARENTH;
      }
    };

    private final char myChar;

    Bracket(char c) {
      myChar = c;
    }

    public boolean isClosing(Bracket bracket) {
      return false;
    }

    boolean isOpening() {
      return false;
    }

    static Bracket forChar(char c) {
      for (Bracket bracket : Bracket.values()) {
        if (bracket.myChar == c) return bracket;
      }
      return null;
    }
  }
}
