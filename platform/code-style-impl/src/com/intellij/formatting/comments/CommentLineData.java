// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.comments;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
@ApiStatus.Internal
public abstract class CommentLineData {

  protected String line;
  int commentOffset = -1;
  int textStartOffset = -1;
  boolean startsWithLetter = false;
  private final List<TextRange> unbreakableRanges = new ArrayList<>();

  public CommentLineData(@NotNull String line) {
    this.line = line;
  }

  public void addUnbreakableRange(@NotNull TextRange range) {
    unbreakableRanges.add(range);
  }

  protected int calcWrapPos(int rightMargin) {
    if (!hasText()) return -1;
    int indexBeforeRightMargin = getLastIndexBeforeRightMargin(rightMargin);
    String cutLine = line.substring(0, indexBeforeRightMargin);
    int wrapPos = cutLine.lastIndexOf(' ');
    if (wrapPos < textStartOffset) {
      wrapPos = line.indexOf(' ', textStartOffset);
      if (wrapPos != beforeUnbreakableRange(wrapPos)) return -1;
    }
    return wrapPos;
  }

  public boolean hasText() {
    return textStartOffset > commentOffset;
  }

  public boolean canBeMergedWithPrevious() {
    return hasText() && startsWithLetter;
  }

  public @NotNull String getText() {
    if (hasText()) {
      return line.substring(textStartOffset);
    }
    return "";
  }

  public void merge(@NotNull CommentLineData lineData) {
    for (TextRange range  : lineData.unbreakableRanges) {
      unbreakableRanges.add(range.shiftRight(line.length() + 1));
    }
    line = line + " " + lineData.getText();
  }

  public int getLineLength() {
    int len = 0;
    for (int i = 0; i < line.length(); i ++) {
      char c = line.charAt(i);
      len = getNextCol(len, c);
    }
    return len;
  }

  public @Nullable Pair<String,String> splitLine(int rightMargin) {
    int wrapPos = calcWrapPos(rightMargin);
    if (wrapPos >= 0) return new Pair<>(line.substring(0, wrapPos), line.substring(wrapPos));
    return null;
  }

  public @NotNull String getLine() {
    return line;
  }

  public boolean isTagLine() {
    return false;
  }

  public void setTagLine(boolean isTagLine) {}

  public @NotNull String getLinePrefix() {
    return "";
  }

  public int getCommentOffset() {
    return commentOffset;
  }

  public void setCommentOffset(int commentOffset) {
    this.commentOffset = commentOffset;
  }

  public int getTextStartOffset() {
    return textStartOffset;
  }

  public void setTextStartOffset(int textStartOffset) {
    this.textStartOffset = textStartOffset;
  }

  protected abstract int getTabSize();

  private int getLastIndexBeforeRightMargin(int rightMargin) {
    int col = 0;
    int i = 0;
    while (col < rightMargin) {
      if (i < line.length()) {
        char c = line.charAt(i);
        col = getNextCol(col, c);
        if (col > rightMargin) break;
        i ++;
      }
      else {
        i --;
        break;
      }
    }
    return beforeUnbreakableRange(i);
  }

  private int beforeUnbreakableRange(int i) {
    for (TextRange range: unbreakableRanges) {
      if (range.contains(i)) return range.getStartOffset();
    }
    return i;
  }

  private int getNextCol(int currCol, char c) {
    return currCol + (c == '\t' ? getTabSize() : 1);
  }
}
