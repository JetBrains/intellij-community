// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Processor;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

@ApiStatus.Internal
public class FrozenDocument implements DocumentEx {
  private final ImmutableCharSequence myText;
  private volatile @Nullable SoftReference<LineSet> myLineSet;
  private final long myStamp;
  private volatile SoftReference<String> myTextString;

  FrozenDocument(@NotNull ImmutableCharSequence text, @Nullable LineSet lineSet, long stamp, @Nullable String textString) {
    myText = text;
    myLineSet = lineSet == null ? null : new SoftReference<>(lineSet);
    myStamp = stamp;
    myTextString = textString == null ? null : new SoftReference<>(textString);
  }

  private @NotNull LineSet getLineSet() {
    LineSet lineSet = dereference(myLineSet);
    if (lineSet == null) {
      myLineSet = new SoftReference<>(lineSet = LineSet.createLineSet(myText));
    }
    return lineSet;
  }

  public @NotNull FrozenDocument applyEvent(@NotNull DocumentEvent event, int newStamp) {
    int offset = event.getOffset();
    int oldEnd = offset + event.getOldLength();
    ImmutableCharSequence newText = myText.replace(offset, oldEnd, event.getNewFragment());
    LineSet newLineSet = getLineSet().update(myText, offset, oldEnd, event.getNewFragment(), event.isWholeTextReplaced());
    return new FrozenDocument(newText, newLineSet, newStamp, null);
  }

  @Override
  public @NotNull LineIterator createLineIterator() {
    return getLineSet().createIterator();
  }

  @Override
  public void setModificationStamp(long modificationStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearLineModificationFlags() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean processRangeMarkers(@NotNull Processor<? super RangeMarker> processor) {
    return true;
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<? super RangeMarker> processor) {
    return true;
  }

  @Override
  public @NotNull String getText() {
    String s = dereference(myTextString);
    if (s == null) {
      myTextString = new SoftReference<>(s = myText.toString());
    }
    return s;
  }

  @Override
  public @NotNull String getText(@NotNull TextRange range) {
    return myText.subSequence(range.getStartOffset(), range.getEndOffset()).toString();
  }

  @Override
  public @NotNull CharSequence getCharsSequence() {
    return myText;
  }

  @Override
  public @NotNull CharSequence getImmutableCharSequence() {
    return myText;
  }

  @Override
  public int getLineCount() {
    return getLineSet().getLineCount();
  }

  @Override
  public int getLineNumber(int offset) {
    return getLineSet().findLineIndex(offset);
  }

  @Override
  public int getLineStartOffset(int line) {
    if (line == 0) return 0; // otherwise it crashed for zero-length document
    return getLineSet().getLineStart(line);
  }

  @Override
  public int getLineEndOffset(int line) {
    if (getTextLength() == 0 && line == 0) return 0;
    int result = getLineSet().getLineEnd(line) - getLineSeparatorLength(line);
    assert result >= 0;
    return result;
  }

  @Override
  public void insertString(int offset, @NotNull CharSequence s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteString(int startOffset, int endOffset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public long getModificationStamp() {
    return myStamp;
  }

  @Override
  public @NotNull RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setReadOnly(boolean isReadOnly) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeGuardedBlock(@NotNull RangeMarker block) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable RangeMarker getOffsetGuard(int offset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable RangeMarker getRangeGuard(int start, int end) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void startGuardedBlockChecking() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void stopGuardedBlockChecking() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getLineSeparatorLength(int line) {
    return getLineSet().getSeparatorLength(line);
  }

  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    throw new UnsupportedOperationException();
  }
}
