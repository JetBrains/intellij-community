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

import org.jetbrains.annotations.ApiStatus;
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
   * from the {@code Runnable} passed to {@link #runBatchFoldingOperation(Runnable)}.
   * The region is initially not folded.
   *
   * @param startOffset     the start offset of the region to fold.
   * @param endOffset       the end offset of the region to fold.
   * @param placeholderText the text to display instead of the region contents when the region is folded.
   * @return the fold region, or {@code null} if folding is currently disabled or corresponding region cannot be added (e.g. if it
   * intersects with another existing region)
   */
  @Nullable
  FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText);

  /**
   * Removes the specified fold region. This method must be called
   * from the {@code Runnable} passed to {@link #runBatchFoldingOperation(Runnable)}.
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
  FoldRegion @NotNull [] getAllFoldRegions();

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
   * Returns collapsed folded region at a given offset or {@code null} if there's no such region. Returned region will satisfy the
   * following condition: region.getStartOffset() <= offset < region.getEndOffset()
   * <br>
   * This method can return incorrect data if it's invoked in the context of {@link #runBatchFoldingOperation(Runnable)} invocation.
   *
   * @see #isOffsetCollapsed(int)
   */
  @Nullable
  FoldRegion getCollapsedRegionAtOffset(int offset);

  /**
   * Returns fold region with given boundaries, if it exists, or {@code null} otherwise.
   */
  @Nullable
  FoldRegion getFoldRegion(int startOffset, int endOffset);

  /**
   * Runs an operation which is allowed to modify fold regions in the editor by calling
   * {@link #addFoldRegion(int, int, String)} and {@link #removeFoldRegion(FoldRegion)}.
   *
   * @param operation the operation to execute.
   */
  default void runBatchFoldingOperation(@NotNull Runnable operation) {
    runBatchFoldingOperation(operation, true, true);
  }

  /**
   * @deprecated Passing {@code false} for {@code moveCaretFromCollapsedRegion} might leave caret in an inconsistent state
   * after the operation. Use {@link #runBatchFoldingOperation(Runnable)} instead.
   */
  @Deprecated(forRemoval = true)
  void runBatchFoldingOperation(@NotNull Runnable operation, boolean moveCaretFromCollapsedRegion);

  default void runBatchFoldingOperationDoNotCollapseCaret(@NotNull Runnable operation) {
    runBatchFoldingOperation(operation, false, true);
  }

  /**
   * Performs folding model changes (creation/deletion/expanding/collapsing of fold regions).
   *
   * @param allowMovingCaret If {@code false}, requests to collapse a region containing caret won't be processed. If {@code true} -
   *                         corresponding operation will be performed with caret automatically moved to the region's start offset
   *                         (original caret position is remembered and is restored on region expansion).
   * @param keepRelativeCaretPosition If {@code true}, editor scrolling position will be adjusted after the operation, so that vertical
   *                                  caret position will remain unchanged (if caret is not visible at operation start, top left corner
   *                                  of editor will be used as an anchor instead). If {@code false}, no scrolling adjustment will be done.
   */
  void runBatchFoldingOperation(@NotNull Runnable operation, boolean allowMovingCaret, boolean keepRelativeCaretPosition);

  /**
   * Creates a fold region with custom representation (defined by the provided renderer). Created region spans whole document lines, and
   * always remains in a collapsed state (it can be removed, but not expanded).
   *
   * @param startLine starting document line in a target line range to fold (inclusive)
   * @param endLine ending document line in a target line range to fold (inclusive)
   * @param renderer Renderer defining the representation of fold region (size and rendered content). One renderer can be re-used for
   *                 multiple fold regions.
   * @return resulting fold region, or {@code null} if it cannot be created (e.g. due to unsupported overlapping with already existing
   * regions)
   */
  @ApiStatus.Experimental
  default @Nullable CustomFoldRegion addCustomLinesFolding(int startLine, int endLine, @NotNull CustomFoldRegionRenderer renderer) {
    return null;
  }
}
