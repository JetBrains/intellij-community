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
package com.intellij.diff.comparison;

import org.jetbrains.annotations.NotNull;

abstract class TextChunk {
  @NotNull private final CharSequence myText;
  private final int myOffset1;
  private final int myOffset2;

  public TextChunk(@NotNull CharSequence text, int offset1, int offset2) {
    myText = text;
    myOffset1 = offset1;
    myOffset2 = offset2;
  }

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  @NotNull
  public CharSequence getContent() {
    return myText.subSequence(myOffset1, myOffset2);
  }

  @NotNull
  public CharSequence getOriginalText() {
    return myText;
  }

  public int getOffset1() {
    return myOffset1;
  }

  public int getOffset2() {
    return myOffset2;
  }

  @Override
  public String toString() {
    return getContent().toString();
  }
}
