// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.formatting;

import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IndentData {
  private final int myIndentSpaces;
  private final int mySpaces;

  public IndentData(final int indentSpaces, final int spaces) {
    assert indentSpaces >= 0 : "Indent spaces can't be negative";
    myIndentSpaces = indentSpaces;
    mySpaces = spaces;
  }

  public IndentData(final int indentSpaces) {
    this(indentSpaces, 0);
  }

  public int getTotalSpaces() {
    return mySpaces + myIndentSpaces;
  }

  public int getIndentSpaces() {
    return myIndentSpaces;
  }

  public int getSpaces() {
    return mySpaces;
  }

  public IndentData add(final IndentData childOffset) {
    return new IndentData(myIndentSpaces + childOffset.getIndentSpaces(), mySpaces + childOffset.getSpaces());
  }

  public IndentData add(final WhiteSpace whiteSpace) {
    return new IndentData(myIndentSpaces + whiteSpace.getIndentOffset(), mySpaces + whiteSpace.getSpaces());
  }

  public boolean isEmpty() {
    return myIndentSpaces == 0 && mySpaces == 0;
  }

  public IndentInfo createIndentInfo() {
    return new IndentInfo(0, myIndentSpaces, mySpaces);
  }

  @Override
  public String toString() {
    return "spaces=" + mySpaces + ", indent spaces=" + myIndentSpaces;
  }

  public static IndentData createFrom(@NotNull CharSequence chars, int startOffset, int endOffset, int tabSize) {
    assert tabSize > 0 : "Invalid tab size: " + tabSize;
    int indent = 0;
    int alignment = 0;
    boolean hasTabs = false;
    boolean isInAlignmentArea = false;
    for (int i = startOffset; i < Math.min(chars.length(), endOffset); i ++) {
      char c = chars.charAt(i);
      switch (c) {
        case ' ' -> {
          if (hasTabs) {
            isInAlignmentArea = true;
            alignment++;
          }
          else {
            indent++;
          }
        }
        case '\t' -> {
          if (isInAlignmentArea) {
            alignment = (alignment / tabSize + 1) * tabSize;
          }
          else {
            hasTabs = true;
            indent += tabSize;
          }
        }
        default -> throw new InvalidDataException("Unexpected indent character: '" + c + "'");
      }
    }
    return new IndentData(indent, alignment);
  }

  public static @Nullable IndentData min(@Nullable IndentData first, @Nullable IndentData second) {
    if (first == null) return second;
    if (second == null) return first;
    return first.getTotalSpaces() < second.getTotalSpaces() ? first : second;
  }
}
