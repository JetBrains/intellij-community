/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

public class FormatTextRange {
  private TextRange myRange;
  private final boolean myProcessHeadingWhitespace;

  public FormatTextRange(TextRange range, boolean processHeadingWhitespace) {
    myRange = range;
    myProcessHeadingWhitespace = processHeadingWhitespace;
  }
  
  public boolean isProcessHeadingWhitespace() {
    return myProcessHeadingWhitespace;
  }

  public boolean isWhitespaceReadOnly(@Nullable TextRange range) {
    if (myRange == null) {
      return false;
    }
    if (range == null || range.getStartOffset() >= myRange.getEndOffset()) return true;
    if (myProcessHeadingWhitespace) {
      return range.getEndOffset() < myRange.getStartOffset();
    }
    else {
      return range.getEndOffset() <= myRange.getStartOffset();
    }
  }

  public int getStartOffset() {
    return myRange.getStartOffset();
  }

  public boolean isReadOnly(TextRange range) {
    if (myRange == null) {
      return false;
    }

    return range.getStartOffset() > myRange.getEndOffset() || range.getEndOffset() < myRange.getStartOffset();
  }

  public TextRange getTextRange() {
    return myRange;
  }

  public void setTextRange(TextRange range) {
    myRange = range;
  }

  public TextRange getNonEmptyTextRange() {
    int endOffset = myRange.getStartOffset() == myRange.getEndOffset()
                 ? myRange.getEndOffset() + 1
                 : myRange.getEndOffset();
    
    return new TextRange(myRange.getStartOffset(), endOffset);
  }

  @Override
  public String toString() {
    return myRange.toString() + (myProcessHeadingWhitespace ? "+" : "");
  }
}