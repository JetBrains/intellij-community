// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MarkupModelEx extends MarkupModel {
  void dispose();

  @Nullable
  RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes);

  void fireAttributesChanged(@NotNull RangeHighlighterEx segmentHighlighter, boolean renderersChanged, boolean fontStyleChanged);

  void fireAfterAdded(@NotNull RangeHighlighterEx segmentHighlighter);

  void fireBeforeRemoved(@NotNull RangeHighlighterEx segmentHighlighter);

  boolean containsHighlighter(@NotNull RangeHighlighter highlighter);

  void addRangeHighlighter(@NotNull RangeHighlighterEx marker,
                           int start,
                           int end,
                           boolean greedyToLeft,
                           boolean greedyToRight,
                           int layer);

  void addMarkupModelListener(@NotNull Disposable parentDisposable, @NotNull MarkupModelListener listener);

  void setRangeHighlighterAttributes(@NotNull RangeHighlighter highlighter, @NotNull TextAttributes textAttributes);

  boolean processRangeHighlightersOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor);
  boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor);

  @NotNull
  MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset);

  @NotNull
  MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset,
                                                         int endOffset,
                                                         boolean onlyRenderedInGutter,
                                                         boolean onlyRenderedInScrollBar);

  // optimization: creates highlighter and fires only one event: highlighterCreated
  @NotNull
  RangeHighlighterEx addRangeHighlighterAndChangeAttributes(int startOffset,
                                                            int endOffset,
                                                            int layer,
                                                            TextAttributes textAttributes,
                                                            @NotNull HighlighterTargetArea targetArea,
                                                            boolean isPersistent,
                                                            Consumer<? super RangeHighlighterEx> changeAttributesAction);

  // runs change attributes action and fires highlighterChanged event if there were changes
  void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter, @NotNull Consumer<? super RangeHighlighterEx> changeAttributesAction);
}
