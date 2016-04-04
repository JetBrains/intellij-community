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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is mock implementation to be used in null-object pattern where necessary.
 * @author max
 */
public class EmptyMarkupModel implements MarkupModelEx {
  private final Document myDocument;

  public EmptyMarkupModel(final Document document) {
    myDocument = document;
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @Override
  @NotNull
  public RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              @Nullable TextAttributes textAttributes,
                                              @NotNull HighlighterTargetArea targetArea) {
    throw new ProcessCanceledException();
  }

  @NotNull
  @Override
  public RangeHighlighterEx addRangeHighlighterAndChangeAttributes(int startOffset,
                                                                   int endOffset,
                                                                   int layer,
                                                                   TextAttributes textAttributes,
                                                                   @NotNull HighlighterTargetArea targetArea,
                                                                   boolean isPersistent,
                                                                   Consumer<RangeHighlighterEx> changeAttributesAction) {
    throw new ProcessCanceledException();
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<RangeHighlighterEx> changeAttributesAction) {
  }

  @Override
  @NotNull
  public RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    throw new ProcessCanceledException();
  }

  @Override
  public void removeHighlighter(@NotNull RangeHighlighter rangeHighlighter) {
  }

  @Override
  public void removeAllHighlighters() {
  }

  @Override
  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    return RangeHighlighter.EMPTY_ARRAY;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
  }

  @Override
  public void dispose() {
  }

  @Override
  public RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    return null;
  }

  @Override
  public boolean containsHighlighter(@NotNull RangeHighlighter highlighter) {
    return false;
  }

  @Override
  public void addMarkupModelListener(@NotNull Disposable parentDisposable, @NotNull MarkupModelListener listener) {
  }

  @Override
  public void setRangeHighlighterAttributes(@NotNull final RangeHighlighter highlighter, @NotNull final TextAttributes textAttributes) {

  }

  @Override
  public boolean processRangeHighlightersOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return false;
  }

  @Override
  public boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return false;
  }

  @NotNull
  @Override
  public MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    return MarkupIterator.EMPTY;
  }

  @Override
  public void fireAttributesChanged(@NotNull RangeHighlighterEx segmentHighlighter, boolean renderersChanged, boolean fontStyleChanged) {

  }

  @Override
  public void fireAfterAdded(@NotNull RangeHighlighterEx segmentHighlighter) {

  }

  @Override
  public void fireBeforeRemoved(@NotNull RangeHighlighterEx segmentHighlighter) {

  }

  @Override
  public void addRangeHighlighter(@NotNull RangeHighlighterEx marker, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {

  }
}
