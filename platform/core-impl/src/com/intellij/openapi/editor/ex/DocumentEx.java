/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.ex;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public interface DocumentEx extends Document {
  default void setStripTrailingSpacesEnabled(boolean isEnabled) {
  }

  @NotNull
  LineIterator createLineIterator();

  void setModificationStamp(long modificationStamp);

  default void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
  }

  default void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
  }

  void replaceText(@NotNull CharSequence chars, long newModificationStamp);

  /**
   * Moves text from the {@code [srcStart; srcEnd)} range to the {@code dstOffset} offset.
   * <p/>
   * The benefit to use this method over usual {@link #deleteString(int, int)} and {@link #replaceString(int, int, CharSequence)}
   * is that {@link #createRangeMarker(int, int, boolean) range markers} from the {@code [srcStart; srcEnd)} range have
   * a chance to be preserved. Default implementation doesn't preserve range markers, but has the same effect in terms of resulting
   * text content.
   *
   * @param srcStart  start offset of the text to move (inclusive)
   * @param srcEnd    end offset of the text to move (exclusive)
   * @param dstOffset the offset to insert the text to. Must be outside of the (srcStart, srcEnd) range.
   */
  default void moveText(int srcStart, int srcEnd, int dstOffset) {
    assert srcStart <= srcEnd && (dstOffset <= srcStart || dstOffset >= srcEnd);
    if (srcStart < srcEnd && (dstOffset < srcStart || dstOffset > srcEnd)) {
      String fragment = getText(new TextRange(srcStart, srcEnd));
      insertString(dstOffset, fragment);
      int shift = dstOffset < srcStart ? srcEnd - srcStart : 0;
      deleteString(srcStart + shift, srcEnd + shift);
    }
  }

  default void suppressGuardedExceptions() {
  }
  default void unSuppressGuardedExceptions() {
  }

  default boolean isInEventsHandling() {
    return false;
  }

  default void clearLineModificationFlags() {
  }

  boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker);

  void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                           int start,
                           int end,
                           boolean greedyToLeft,
                           boolean greedyToRight,
                           int layer);

  @NotNull
  default List<RangeMarker> getGuardedBlocks() {
    return Collections.emptyList();
  }

  /**
   * Get all range markers
   * and hand them to the {@code processor} in their {@link RangeMarker#getStartOffset()} order
   */
  boolean processRangeMarkers(@NotNull Processor<? super RangeMarker> processor);

  /**
   * Get range markers which {@link TextRange#intersects(int, int)} the specified range
   * and hand them to the {@code processor} in their {@link RangeMarker#getStartOffset()} order
   */
  boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<? super RangeMarker> processor);

  /**
   * @return modification stamp. Guaranteed to be strictly increasing on each change unlike the {@link #getModificationStamp()} which can change arbitrarily.
   */
  default int getModificationSequence() {
    return 0;
  }
}



