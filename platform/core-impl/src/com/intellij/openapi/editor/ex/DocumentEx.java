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
package com.intellij.openapi.editor.ex;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DocumentEx extends Document {
  void setStripTrailingSpacesEnabled(boolean isEnabled);

  @NotNull
  LineIterator createLineIterator();

  void setModificationStamp(long modificationStamp);

  void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener);

  void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener);

  void replaceText(@NotNull CharSequence chars, long newModificationStamp);

  /**
   * Moves text from the <code>[srcStart; srcEnd)</code> range to the <code>dstOffset</code> offset.
   * <p/>
   * The benefit to use this method over usual {@link #deleteString(int, int)} and {@link #replaceString(int, int, CharSequence)}
   * is that {@link #createRangeMarker(int, int, boolean) range markers} from the <code>[srcStart; srcEnd)</code> range have
   * a chance to be preserved.
   *
   * @param srcStart  start offset of the text to move (inclusive)
   * @param srcEnd    end offset of the text to move (exclusive)
   * @param dstOffset the offset to insert the text to. Must be outside of the (srcStart, srcEnd) range.
   */
  void moveText(int srcStart, int srcEnd, int dstOffset);

  int getListenersCount();

  void suppressGuardedExceptions();
  void unSuppressGuardedExceptions();

  boolean isInEventsHandling();

  void clearLineModificationFlags();

  boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker);

  void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                           int start,
                           int end,
                           boolean greedyToLeft,
                           boolean greedyToRight,
                           int layer);

  boolean isInBulkUpdate();

  /**
   * Enters or exits 'bulk' mode for processing of document changes. Bulk mode should be used when a large number of document changes
   * are applied in batch (without user interaction for each change). In this mode, to improve performance, some activities that usually
   * happen on each document change will be muted, with reconciliation happening on bulk mode exit.
   * <br>
   * Certain operations shouldn't be invoked in bulk mode as they can return invalid results or lead to exception. They include: querying 
   * or updating folding or soft wrap data, editor position recalculation functions (offset to logical position, logical to visual position, 
   * etc), querying or updating caret position or selection state. 
   */
  void setInBulkUpdate(boolean value);

  @NotNull
  List<RangeMarker> getGuardedBlocks();


  /**
   * Get all range markers
   * and hand them to the {@code processor} in their {@link RangeMarker#getStartOffset()} order
   */
  boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor);

  /**
   * Get range markers which {@link com.intellij.openapi.util.TextRange#intersects(int, int)} the specified range
   * and hand them to the {@code processor} in their {@link RangeMarker#getStartOffset()} order
   */
  boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor);

  /**
   * @return modification stamp. Guaranteed to be strictly increasing on each change unlike the {@link #getModificationStamp()} which can change arbitrarily.
   */
  int getModificationSequence();
}



