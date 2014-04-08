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
package com.intellij.openapi.editor.richcopy.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 3/23/13 3:08 PM
 */
public class Text implements OutputInfo {
  
  private final int myStartOffset;
  private final int myEndOffset;

  public Text(int startOffset, int endOffset) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public Text(char c) {
    myStartOffset = -1;
    myEndOffset = c;
  }

  @Override
  public void invite(@NotNull OutputInfoVisitor visitor) {
    visitor.visit(this);
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myStartOffset == -1 ? 0 : myEndOffset;
  }

  public char getCharAt(CharSequence charSequence, int offset) {
    return myStartOffset == -1 ? (char)myEndOffset : charSequence.charAt(offset);
  }

  @Override
  public int hashCode() {
    int result = myStartOffset;
    result = 31 * result + myEndOffset;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Text text = (Text)o;
    return myEndOffset == text.myEndOffset && myStartOffset == text.myStartOffset;
  }

  @Override
  public String toString() {
    return myStartOffset == -1 ? String.format("text=%c", myEndOffset) : String.format("text=%d-%d", myStartOffset, myEndOffset);
  }
}
