// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MarkupModelEx extends MarkupModel {
  void dispose();


  @Nullable
  RangeHighlighterEx addPersistentLineHighlighter(@Nullable TextAttributesKey textAttributesKey, int lineNumber, int layer);

  /**
   * Consider using {@link #addPersistentLineHighlighter(TextAttributesKey, int, int)}
   * unless it's really necessary.
   * Creating a highlighter with hard-coded {@link TextAttributes} makes it stay the same in all {@link EditorColorsScheme}
   * An editor can provide a custom scheme different from the global one, also a user can change the global scheme explicitly.
   * Using the overload taking a {@link TextAttributesKey} will make the platform take care of all these cases.
   */
  @Nullable
  RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, @Nullable TextAttributes textAttributes);

  void fireAttributesChanged(@NotNull RangeHighlighterEx segmentHighlighter, boolean renderersChanged, boolean fontStyleOrColorChanged);

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

  /**
   * @deprecated onlyRenderedInScrollBar doesn't affect anything
   */
  @Deprecated
  @NotNull
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  default MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset,
                                                         int endOffset,
                                                         boolean onlyRenderedInGutter,
                                                         boolean onlyRenderedInScrollBar) {
    return overlappingIterator(startOffset, endOffset, onlyRenderedInGutter);
  }

  @NotNull
  MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset,
                                                         int endOffset,
                                                         boolean onlyRenderedInGutter);

  // optimization: creates highlighter and fires only one event: highlighterCreated
  @NotNull
  RangeHighlighterEx addRangeHighlighterAndChangeAttributes(@Nullable TextAttributesKey textAttributesKey,
                                                            int startOffset,
                                                            int endOffset,
                                                            int layer,
                                                            @NotNull HighlighterTargetArea targetArea,
                                                            boolean isPersistent,
                                                            @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction);

  /**
   * Consider using {@link #addRangeHighlighterAndChangeAttributes(TextAttributesKey, int, int, int, HighlighterTargetArea, boolean, Consumer)}
   * unless it's really necessary.
   * Creating a highlighter with hard-coded {@link TextAttributes} makes it stay the same in all {@link EditorColorsScheme}
   * An editor can provide a custom scheme different from the global one, also a user can change the global scheme explicitly.
   * Using the overload taking a {@link TextAttributesKey} will make the platform take care of all these cases.
   */
  @NotNull
  default RangeHighlighterEx addRangeHighlighterAndChangeAttributes(int startOffset,
                                                                    int endOffset,
                                                                    int layer,
                                                                    TextAttributes textAttributes,
                                                                    @NotNull HighlighterTargetArea targetArea,
                                                                    boolean isPersistent,
                                                                    @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    return addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, layer, targetArea, isPersistent, ex -> {
      if (textAttributes != null) {
        ex.setTextAttributes(textAttributes);
      }
      if (changeAttributesAction != null) {
        changeAttributesAction.consume(ex);
      }
    });
  }

  // runs change attributes action and fires highlighterChanged event if there were changes
  void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter, @NotNull Consumer<? super RangeHighlighterEx> changeAttributesAction);
}
