// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.DocumentTextPatch;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Computes a text replacement: trims the common prefix/suffix to produce the narrowed change-event parameters,
 * and builds the {@link DocumentTextPatch} for the resulting snapshot (reusing the caller's sequence as the whole
 * new text when the entire document is replaced).
 */
@ApiStatus.Internal
@VisibleForTesting
public final class OptimizedTextReplacement { // TODO: refactor me
  private final ImmutableCharSequence wholeText;
  private final int initialStartOffset;
  private final int initialEndOffset;
  private final CharSequence initialNewFragment;
  private final boolean initialWholeTextReplaced;
  private final long newModStamp;
  private final boolean clearLineFlags;

  private DocumentTextPatch patch;
  private int startOffset;
  private int endOffset;
  private int moveOffset;
  private boolean wholeTextReplaced;
  private CharSequence oldFragment;
  private CharSequence newFragment;

  public OptimizedTextReplacement(
    @NotNull ImmutableCharSequence wholeText,
    int initialStartOffset,
    int initialEndOffset,
    int initialMoveOffset,
    @NotNull CharSequence initialNewFragment,
    boolean initialWholeTextReplaced,
    long newModStamp,
    boolean clearLineFlags
  ) {
    this.wholeText = wholeText;
    this.initialStartOffset = initialStartOffset;
    this.initialEndOffset = initialEndOffset;
    this.initialNewFragment = initialNewFragment;
    this.initialWholeTextReplaced = initialWholeTextReplaced;
    this.newModStamp = newModStamp;
    this.clearLineFlags = clearLineFlags;

    this.startOffset = initialStartOffset;
    this.endOffset = initialEndOffset;
    this.moveOffset = initialMoveOffset;
    this.wholeTextReplaced = initialWholeTextReplaced;
    this.newFragment = initialNewFragment;
  }

  public boolean perform() {
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
    // the whole-text condition mirrors DocumentEventImpl.isWholeTextReplaced:
    // an empty document cannot have its "whole text replaced"
    boolean patchClearLineFlags = clearLineFlags || (wholeTextReplaced && wholeText.length() != 0);
    // For a whole-text replacement reuse the original (untrimmed) sequence as the new text: prefix/suffix
    // trimming above only narrows the change event, it must not shrink the resulting document text.
    // The full-range check guards against a partial-range request abusing the whole-text flag.
    if (wholeTextReplaced &&
        initialStartOffset == 0 && initialEndOffset == wholeText.length() &&
        initialNewFragment instanceof ImmutableCharSequence) {
      this.patch = DocumentTextPatch.complex(
        0,
        wholeText.length(),
        initialNewFragment,
        newModStamp,
        patchClearLineFlags,
        initialStartOffset,
        initialEndOffset,
        moveOffset
      );
    } else {
      // decouple the event/patch fragment from the caller's possibly mutable sequence
      this.newFragment = ImmutableCharSequence.asImmutable(newFragment);
      this.patch = DocumentTextPatch.complex(
        startOffset,
        endOffset,
        newFragment,
        newModStamp,
        patchClearLineFlags,
        initialStartOffset,
        initialEndOffset,
        moveOffset
      );
    }
    return false;
  }

  public @NotNull DocumentTextPatch getPatch() {
    return patch;
  }

  public int getStartOffset() {
    return startOffset;
  }

  public int getEndOffset() {
    return endOffset;
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
}
