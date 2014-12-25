/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services for controlling and getting information about folded regions in the
 * editor.
 *
 * @see Editor#getFoldingModel()
 */
public interface FoldingModel {
  /**
   * Adds a fold region for the specified range of the document. This method must be called
   * from the <code>Runnable</code> passed to {@link #runBatchFoldingOperation(Runnable)}.
   * The region is initially not folded.
   *
   * @param startOffset     the start offset of the region to fold.
   * @param endOffset       the end offset of the region to fold.
   * @param placeholderText the text to display instead of the region contents when the region is folded.
   * @return the fold region, or <code>null</code> if folding is currently disabled or corresponding region cannot be added (e.g. if it
   * intersects with another existing region)
   */
  @Nullable
  FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText);

  /**
   * Tries to add given region to the folding model. This method must be called
   * from the <code>Runnable</code> passed to {@link #runBatchFoldingOperation(Runnable)}.
   *
   * @return <code>true</code>, if region was added successfully, <code>false</code> if the region cannot be added, e.g. if it
   * intersects with another existing region
   */
  boolean addFoldRegion(@NotNull FoldRegion region);

  /**
   * Removes the specified fold region. This method must be called
   * from the <code>Runnable</code> passed to {@link #runBatchFoldingOperation(Runnable)}.
   *
   * @param region the region to remove.
   */
  void removeFoldRegion(@NotNull FoldRegion region);

  /**
   * Gets the list of all fold regions in the specified editor.
   * Returned array is sorted according to {@link RangeMarker#BY_START_OFFSET} comparator, i.e. first by start offset, then by end offset.
   *
   * @return the array of fold regions, or an empty array if folding is currently disabled.
   */
  @NotNull
  FoldRegion[] getAllFoldRegions();

  /**
   * Checks if the specified offset in the document belongs to a folded region. The region must contain given offset or be located right
   * after given offset, i.e. the following condition must hold: foldStartOffset <= offset < foldEndOffset.
   * <br>
   * This method can return incorrect data if it's invoked in the context of {@link #runBatchFoldingOperation(Runnable)} invocation.
   *
   * @param offset the offset to check.
   * @return true if the offset belongs to a folded region, false otherwise.
   *
   * @see #getCollapsedRegionAtOffset(int)
   */
  boolean isOffsetCollapsed(int offset);

  /**
   * Returns collapsed folded region at a given offset or <code>null</code> if there's no such region. Returned region will satisfy the
   * following condition: region.getStartOffset() <= offset < region.getEndOffset()
   * <br>
   * This method can return incorrect data if it's invoked in the context of {@link #runBatchFoldingOperation(Runnable)} invocation.
   *
   * @see #isOffsetCollapsed(int)
   */
  @Nullable
  FoldRegion getCollapsedRegionAtOffset(int offset);

  /**
   * Returns fold region with given boundaries, if it exists, or <code>null</code> otherwise. 
   */
  @Nullable
  FoldRegion getFoldRegion(int startOffset, int endOffset);

  /**
   * Runs an operation which is allowed to modify fold regions in the editor by calling
   * {@link #addFoldRegion(int, int, String)} and {@link #removeFoldRegion(FoldRegion)}.
   *
   * @param operation the operation to execute.
   */
  void runBatchFoldingOperation(@NotNull Runnable operation);

  /**
   * Runs an operation which is allowed to modify fold regions in the editor by calling
   * {@link #addFoldRegion(int, int, String)} and {@link #removeFoldRegion(FoldRegion)}.
   *
   * @param operation                    the operation to execute.
   * @param moveCaretFromCollapsedRegion flag that identifies whether caret position should be changed if it's located inside
   *                                     collapsed fold region after the operation
   */
  void runBatchFoldingOperation(@NotNull Runnable operation, boolean moveCaretFromCollapsedRegion);

  void runBatchFoldingOperationDoNotCollapseCaret(@NotNull Runnable operation);
}
