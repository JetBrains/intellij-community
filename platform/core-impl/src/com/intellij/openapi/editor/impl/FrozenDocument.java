// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.DocumentSnapshot;
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

@ApiStatus.Internal
public class FrozenDocument implements DocumentEx {
  private final DocumentSnapshot mySnapshot;

  FrozenDocument(@NotNull DocumentSnapshot snapshot) {
    mySnapshot = snapshot;
  }

  public @NotNull FrozenDocument applyEvent(@NotNull DocumentEvent event, int newStamp) {
    int offset = event.getOffset();
    int oldEnd = offset + event.getOldLength();
    ImmutableCharSequence oldWholeText = mySnapshot.text();
    ImmutableCharSequence nextWholeText = oldWholeText.replace(offset, oldEnd, event.getNewFragment());
    DocumentSnapshot newSnapshot = mySnapshot.withText(
      nextWholeText,
      offset,
      oldEnd,
      event.getNewFragment(),
      newStamp,
      event.isWholeTextReplaced(),
      false
    );
    return new FrozenDocument(newSnapshot);
  }

  @NotNull DocumentSnapshot getSnapshot() {
    return mySnapshot;
  }

  @Override
  public @NotNull LineIterator createLineIterator() {
    return mySnapshot.lineIterator();
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
    return mySnapshot.string();
  }

  @Override
  public @NotNull String getText(@NotNull TextRange range) {
    return mySnapshot.string(range);
  }

  @Override
  public @NotNull CharSequence getCharsSequence() {
    return getImmutableCharSequence();
  }

  @Override
  public @NotNull CharSequence getImmutableCharSequence() {
    return mySnapshot.text();
  }

  @Override
  public int getLineCount() {
    return mySnapshot.lineCount();
  }

  @Override
  public int getLineNumber(int offset) {
    return mySnapshot.lineNumber(offset);
  }

  @Override
  public int getLineStartOffset(int line) {
    return mySnapshot.lineStartOffset(line);
  }

  @Override
  public int getLineEndOffset(int line) {
    return mySnapshot.lineEndOffset(line);
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
    return mySnapshot.modStamp();
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
    return mySnapshot.lineSeparatorLength(line);
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
