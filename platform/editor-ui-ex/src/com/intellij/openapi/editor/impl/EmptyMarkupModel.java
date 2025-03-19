// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is mock implementation to be used in null-object pattern where necessary.
 */
@ApiStatus.Internal
public final class EmptyMarkupModel implements MarkupModelEx {
  private final Document myDocument;

  public EmptyMarkupModel(final Document document) {
    myDocument = document;
  }

  @Override
  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public @NotNull RangeHighlighter addRangeHighlighter(@Nullable TextAttributesKey textAttributesKey,
                                                       int startOffset,
                                                       int endOffset,
                                                       int layer,
                                                       @NotNull HighlighterTargetArea targetArea) {
    throw new ProcessCanceledException();
  }

  @Override
  public @NotNull RangeHighlighter addRangeHighlighter(int startOffset,
                                                       int endOffset,
                                                       int layer,
                                                       @Nullable TextAttributes textAttributes,
                                                       @NotNull HighlighterTargetArea targetArea) {
    throw new ProcessCanceledException();
  }

  @Override
  public @NotNull RangeHighlighterEx addRangeHighlighterAndChangeAttributes(@Nullable TextAttributesKey textAttributesKey,
                                                                            int startOffset,
                                                                            int endOffset,
                                                                            int layer,
                                                                            @NotNull HighlighterTargetArea targetArea,
                                                                            boolean isPersistent,
                                                                            @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    throw new ProcessCanceledException();
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<? super RangeHighlighterEx> changeAttributesAction) {
  }

  @Override
  public @NotNull RangeHighlighter addLineHighlighter(@Nullable TextAttributesKey textAttributesKey, int line, int layer) {
    throw new ProcessCanceledException();
  }

  @Override
  public @NotNull RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    throw new ProcessCanceledException();
  }

  @Override
  public void removeHighlighter(@NotNull RangeHighlighter rangeHighlighter) {
  }

  @Override
  public void removeAllHighlighters() {
  }

  @Override
  public RangeHighlighter @NotNull [] getAllHighlighters() {
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
  public RangeHighlighterEx addPersistentLineHighlighter(@Nullable TextAttributesKey textAttributesKey, int lineNumber, int layer) {
    return null;
  }

  @Override
  public @Nullable RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, @Nullable TextAttributes textAttributes) {
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
  public void setRangeHighlighterAttributes(final @NotNull RangeHighlighter highlighter, final @NotNull TextAttributes textAttributes) {

  }

  @Override
  public boolean processRangeHighlightersOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return false;
  }

  @Override
  public boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return false;
  }

  @Override
  public @NotNull MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    return MarkupIterator.EMPTY;
  }

}
