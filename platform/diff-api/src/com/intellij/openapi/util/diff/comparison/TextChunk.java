package com.intellij.openapi.util.diff.comparison;

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
  public CharSequence getText() {
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
