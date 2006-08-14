/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
   * @return the fold region, or null if folding is currently disabled.
   */
  @Nullable
  FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText);

  /**
   * Removes the specified fold region. This method must be called
   * from the <code>Runnable</code> passed to {@link #runBatchFoldingOperation(Runnable)}.
   *
   * @param region the region to remove.
   */
  void removeFoldRegion(FoldRegion region);

  /**
   * Gets the list of all fold regions in the specified editor.
   *
   * @return the array of fold regions, or an empty array if folding is currently disabled.
   */
  @NotNull
  FoldRegion[] getAllFoldRegions();

  /**
   * Checks if the specified offset in the document belongs to a folded region.
   *
   * @param offset the offset to check.
   * @return true if the offset belongs to a folded region, false otherwise.
   */
  boolean isOffsetCollapsed(int offset);

  FoldRegion getCollapsedRegionAtOffset(int offset);

  /**
   * Runs an operation which is allowed to modify fold regions in the editor by calling
   * {@link #addFoldRegion(int, int, String)} and {@link #removeFoldRegion(FoldRegion)}.
   *
   * @param operation the operation to execute.
   */
  void runBatchFoldingOperation(Runnable operation);
}
