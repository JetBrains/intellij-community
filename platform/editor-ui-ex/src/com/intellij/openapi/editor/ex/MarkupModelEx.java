// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.impl.FilteringMarkupIterator;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
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

  /**
   * @deprecated use {@code RangeHighlighterEx.setXXX()} methods to fire changes
   */
  @Deprecated
  @ApiStatus.Internal
  default void fireAttributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {}
  
  boolean containsHighlighter(@NotNull RangeHighlighter highlighter);

  void addMarkupModelListener(@NotNull Disposable parentDisposable, @NotNull MarkupModelListener listener);

  void setRangeHighlighterAttributes(@NotNull RangeHighlighter highlighter, @NotNull TextAttributes textAttributes);

  /**
   * process all highlighters intersecting with [start, end) interval by {@code processor}.
   * You must not do any changes to the markup model in the processor.
   * @return true if no invocations of the {@code processor} returned false
   */
  boolean processRangeHighlightersOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor);
  boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor);

  @NotNull
  MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset);

  /**
   * makes an iterator which enumerates only error-stripe {@link RangeHighlighterEx}s,
   * i.e. those for which {@link com.intellij.openapi.editor.impl.ErrorStripeMarkersModel#isErrorStripeHighlighter} returns true
   */
  @NotNull
  default MarkupIterator<RangeHighlighterEx> overlappingErrorStripeIterator(int startOffset, int endOffset) {
    return new FilteringMarkupIterator<>(overlappingIterator(startOffset, endOffset), h->h.getErrorStripeMarkColor(null) != null);
  }

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
   * @param isPersistent use different logic to update range boundaries on document changes.
   *                     See {@link RangeMarkerImpl#persistentHighlighterUpdate}.
   *
   * @deprecated use {@link #addRangeHighlighterAndChangeAttributes(TextAttributesKey, int, int, int, HighlighterTargetArea, boolean, Consumer)}
   * Creating a highlighter with hard-coded {@link TextAttributes} makes it stay the same in all {@link EditorColorsScheme}
   * An editor can provide a custom scheme different from the global one, also a user can change the global scheme explicitly.
   * Using the overload taking a {@link TextAttributesKey} will make the platform take care of all these cases.
   */
  @Deprecated
  default @NotNull RangeHighlighterEx addRangeHighlighterAndChangeAttributes(int startOffset,
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

  // run change attributes action and fire highlighterChanged event if there were changes
  void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter, @NotNull Consumer<? super RangeHighlighterEx> changeAttributesAction);
}
