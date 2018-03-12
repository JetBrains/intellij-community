// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.reference.SoftReference;
import com.intellij.util.Processor;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class FrozenDocument implements DocumentEx {
  private final ImmutableCharSequence myText;
  @Nullable private volatile LineSet myLineSet;
  private final long myStamp;
  private volatile SoftReference<String> myTextString;

  FrozenDocument(@NotNull ImmutableCharSequence text, @Nullable LineSet lineSet, long stamp, @Nullable String textString) {
    myText = text;
    myLineSet = lineSet;
    myStamp = stamp;
    myTextString = textString == null ? null : new SoftReference<>(textString);
  }

  @NotNull
  private LineSet getLineSet() {
    LineSet lineSet = myLineSet;
    if (lineSet == null) {
      myLineSet = lineSet = LineSet.createLineSet(myText);
    }
    return lineSet;
  }

  public FrozenDocument applyEvent(DocumentEvent event, int newStamp) {
    final int offset = event.getOffset();
    final int oldEnd = offset + event.getOldLength();
    ImmutableCharSequence newText = myText.delete(offset, oldEnd).insert(offset, event.getNewFragment());
    LineSet newLineSet = getLineSet().update(myText, offset, oldEnd, event.getNewFragment(), event.isWholeTextReplaced());
    return new FrozenDocument(newText, newLineSet, newStamp, null);
  }

  @NotNull
  @Override
  public LineIterator createLineIterator() {
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
  public void moveText(int srcStart, int srcEnd, int dstOffset) {
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

  @NotNull
  @Override
  public String getText() {
    String s = SoftReference.dereference(myTextString);
    if (s == null) {
      myTextString = new SoftReference<>(s = myText.toString());
    }
    return s;
  }

  @NotNull
  @Override
  public String getText(@NotNull TextRange range) {
    return myText.subSequence(range.getStartOffset(), range.getEndOffset()).toString();
  }

  @NotNull
  @Override
  public CharSequence getCharsSequence() {
    return myText;
  }

  @NotNull
  @Override
  public CharSequence getImmutableCharSequence() {
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

  @NotNull
  @Override
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setReadOnly(boolean isReadOnly) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeGuardedBlock(@NotNull RangeMarker block) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public RangeMarker getOffsetGuard(int offset) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public RangeMarker getRangeGuard(int start, int end) {
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

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
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
