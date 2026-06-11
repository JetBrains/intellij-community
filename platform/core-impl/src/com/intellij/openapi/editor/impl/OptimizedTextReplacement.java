// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.NotNull;

final class OptimizedTextReplacement {
  private final ImmutableCharSequence wholeText;
  private final int initialStartOffset;
  private final int initialEndOffset;
  private final CharSequence initialNewFragment;
  private final boolean initialWholeTextReplaced;

  private ImmutableCharSequence newWholeText;
  private int startOffset;
  private int endOffset;
  private int moveOffset;
  private boolean wholeTextReplaced;
  private CharSequence oldFragment;
  private CharSequence newFragment;

  OptimizedTextReplacement(
    @NotNull ImmutableCharSequence wholeText,
    int initialStartOffset,
    int initialEndOffset,
    int initialMoveOffset,
    @NotNull CharSequence initialNewFragment,
    boolean initialWholeTextReplaced
  ) {
    this.wholeText = wholeText;
    this.initialStartOffset = initialStartOffset;
    this.initialEndOffset = initialEndOffset;
    this.initialNewFragment = initialNewFragment;
    this.initialWholeTextReplaced = initialWholeTextReplaced;

    this.startOffset = initialStartOffset;
    this.endOffset = initialEndOffset;
    this.moveOffset = initialMoveOffset;
    this.wholeTextReplaced = initialWholeTextReplaced;
    this.newFragment = initialNewFragment;
  }

  boolean perform() {
    int newStartOffset = initialStartOffset;
    int newEndOffset = initialEndOffset;
    int newStartInString = 0;
    int replaceLength = initialNewFragment.length();
    while (newStartInString < replaceLength &&
           newStartOffset < newEndOffset &&
           initialNewFragment.charAt(newStartInString) == wholeText.charAt(newStartOffset)) {
      newStartOffset++;
      newStartInString++;
    }
    if (newStartInString == replaceLength &&
        newStartOffset == newEndOffset &&
        !initialWholeTextReplaced) {
      return true;
    }
    int newEndInString = replaceLength;
    while (newEndOffset > newStartOffset &&
           newEndInString > newStartInString &&
           initialNewFragment.charAt(newEndInString-1) == wholeText.charAt(newEndOffset-1)) {
      newEndInString--;
      newEndOffset--;
    }
    boolean newWholeTextReplaced = initialWholeTextReplaced;
    if (newStartOffset == 0 && newEndOffset == wholeText.length()) {
      newWholeTextReplaced = true;
    }
    this.oldFragment = wholeText.subtext(newStartOffset, newEndOffset);
    this.wholeTextReplaced = newWholeTextReplaced;
    boolean isOptimized = newStartOffset != initialStartOffset || newEndOffset != initialEndOffset;
    if (isOptimized) {
      this.startOffset = newStartOffset;
      this.endOffset = newEndOffset;
      this.newFragment = initialNewFragment.subSequence(newStartInString, newEndInString);
      this.moveOffset = newStartOffset;
    }
    // For a whole-text replacement reuse the original (untrimmed) sequence as the new text: prefix/suffix
    // trimming above only narrows the change event, it must not shrink the resulting document text.
    if (wholeTextReplaced && initialNewFragment instanceof ImmutableCharSequence) {
      this.newWholeText = (ImmutableCharSequence) initialNewFragment;
    } else {
      this.newWholeText = wholeText.replace(startOffset, endOffset, newFragment);
      if (!(newFragment instanceof String)) {
        this.newFragment = newWholeText.subtext(startOffset, startOffset + newFragment.length());
      }
    }
    return false;
  }

  @NotNull ImmutableCharSequence getNewWholeText() {
    return newWholeText;
  }

  int getStartOffset() {
    return startOffset;
  }

  int getEndOffset() {
    return endOffset;
  }

  int getMoveOffset() {
    return moveOffset;
  }

  @NotNull CharSequence getOldFragment() {
    return oldFragment;
  }

  @NotNull CharSequence getNewFragment() {
    return newFragment;
  }

  boolean isWholeTextReplaced() {
    return wholeTextReplaced;
  }

  int getInitialStartOffset() {
    return initialStartOffset;
  }

  int getInitialOldLength() {
    return initialEndOffset - initialStartOffset;
  }
}
