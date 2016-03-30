/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.reference.SoftReference;
import com.intellij.util.Processor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class FrozenDocument implements DocumentEx {
  private final ImmutableText myText;
  private final LineSet myLineSet;
  private final long myStamp;
  private volatile SoftReference<String> myTextString;

  public FrozenDocument(@NotNull ImmutableText text, @NotNull LineSet lineSet, long stamp, @Nullable String textString) {
    myText = text;
    myLineSet = lineSet;
    myStamp = stamp;
    myTextString = textString == null ? null : new SoftReference<String>(textString);
  }

  public FrozenDocument applyEvent(DocumentEvent event, int newStamp) {
    final int offset = event.getOffset();
    final int oldEnd = offset + event.getOldLength();
    final ImmutableText newText = myText.delete(offset, oldEnd).insert(offset, event.getNewFragment());
    final LineSet newLineSet = myLineSet.update(myText, offset, oldEnd, event.getNewFragment(), event.isWholeTextReplaced());
    return new FrozenDocument(newText, newLineSet, newStamp, null);
  }

  @Override
  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public LineIterator createLineIterator() {
    return myLineSet.createIterator();
  }

  @Override
  public void setModificationStamp(long modificationStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
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
  public int getListenersCount() {
    return 0;
  }

  @Override
  public void suppressGuardedExceptions() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void unSuppressGuardedExceptions() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInEventsHandling() {
    return false;
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
  public boolean isInBulkUpdate() {
    return false;
  }

  @Override
  public void setInBulkUpdate(boolean value) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<RangeMarker> getGuardedBlocks() {
    return Collections.emptyList();
  }

  @Override
  public boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor) {
    return true;
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor) {
    return true;
  }

  @NotNull
  @Override
  public String getText() {
    String s = SoftReference.dereference(myTextString);
    if (s == null) {
      myTextString = new SoftReference<String>(s = myText.toString());
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

  @NotNull
  @Override
  public char[] getChars() {
    return CharArrayUtil.fromSequence(myText);
  }

  @Override
  public int getTextLength() {
    return myText.length();
  }

  @Override
  public int getLineCount() {
    return myLineSet.getLineCount();
  }

  @Override
  public int getLineNumber(int offset) {
    return myLineSet.findLineIndex(offset);
  }

  @Override
  public int getLineStartOffset(int line) {
    if (line == 0) return 0; // otherwise it crashed for zero-length document
    return myLineSet.getLineStart(line);
  }

  @Override
  public int getLineEndOffset(int line) {
    if (getTextLength() == 0 && line == 0) return 0;
    int result = myLineSet.getLineEnd(line) - getLineSeparatorLength(line);
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
  public void fireReadOnlyModificationAttempt() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeDocumentListener(@NotNull DocumentListener listener) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public RangeMarker createRangeMarker(int startOffset, int endOffset) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
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
  public void setCyclicBufferSize(int bufferSize) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public RangeMarker createRangeMarker(@NotNull TextRange textRange) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getLineSeparatorLength(int line) {
    return myLineSet.getSeparatorLength(line);
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
  public int getModificationSequence() {
    return 0;
  }
}
