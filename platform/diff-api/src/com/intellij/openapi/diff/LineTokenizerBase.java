// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class LineTokenizerBase<T> {
  private int myIndex = 0;
  private int myLineSeparatorStart = -1;
  private int myLineSeparatorEnd = -1;

  protected abstract void addLine(List<? super T> lines, int start, int end, boolean appendNewLine);

  protected abstract char charAt(int index);

  protected abstract int length();

  @NotNull
  protected abstract String substring(int start, int end);

  public void doExecute(List<? super T> lines) {
    while (notAtEnd()) {
      int begin = myIndex;
      skipToEOL();
      int endIndex = myIndex;
      boolean appendNewLine = false;

      if (notAtEnd() && isAtEOL()) {
        if (charAt(endIndex) == '\n') {
          endIndex++;
        }
        else {
          appendNewLine = true;
        }
        skipEOL();
      }

      addLine(lines, begin, endIndex, appendNewLine);
    }
  }

  private void skipEOL() {
    int eolStart = myIndex;
    boolean nFound = false;
    boolean rFound = false;
    while (notAtEnd()) {
      boolean n = charAt(myIndex) == '\n';
      boolean r = charAt(myIndex) == '\r';
      if (!n && !r) {
        break;
      }
      if ((nFound && n) || (rFound && r)) {
        break;
      }
      nFound |= n;
      rFound |= r;
      myIndex++;
    }
    if (myLineSeparatorStart == -1) {
      myLineSeparatorStart = eolStart;
      myLineSeparatorEnd = myIndex;
    }
  }

  @Nullable
  public String getLineSeparator() {
    if (myLineSeparatorStart == -1) return null;
    return substring(myLineSeparatorStart, myLineSeparatorEnd);
  }

  private void skipToEOL() {
    while (notAtEnd() && !isAtEOL()) {
      myIndex++;
    }
  }

  private boolean notAtEnd() {
    return myIndex < length();
  }

  private boolean isAtEOL() {
    return charAt(myIndex) == '\r' || charAt(myIndex) == '\n';
  }
}