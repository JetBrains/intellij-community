/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface PsiFileEx extends PsiFile {
  /**
   * A key used to determine whether batch reference processing is enabled for a specific {@link PsiFile}.
   * This mode implies that the corresponding file may be intensively analyzed, so it may be
   * efficient to build some cache to process the sequential requests without additional work.
   * <p>
   * {@link PsiFileEx#isBatchReferenceProcessingEnabled } should be used to check that fact.
   *
   * @see PsiFileEx#isBatchReferenceProcessingEnabled
   */
  Key<Boolean> BATCH_REFERENCE_PROCESSING = Key.create("BATCH_REFERENCE_PROCESSING");

  boolean isContentsLoaded();

  void onContentReload();

  void markInvalidated();

  /**
   * Determines whether batch reference processing is enabled for the specified {@link PsiFile}.
   *
   * @param file the {@link PsiFile} to check
   * @return {@code true} if batch reference processing is enabled, {@code false} otherwise
   * @see PsiFileEx#BATCH_REFERENCE_PROCESSING
   */
  @ApiStatus.Experimental
  static boolean isBatchReferenceProcessingEnabled(@NotNull PsiFile file) {
    if (file.getUserData(BATCH_REFERENCE_PROCESSING) != Boolean.TRUE) return false;

    return BatchReferenceProcessingSuppressor.EP_NAME.findFirstSafe(suppressor -> suppressor.isSuppressed(file)) == null;
  }


  /**
   * A suppressor interface for batch reference processing.
   * <p>
   * Implementations of this interface can suppress batch reference processing for specific {@link PsiFile} instances.
   *
   * @see PsiFileEx#BATCH_REFERENCE_PROCESSING
   * @see BatchReferenceProcessingSuppressor#EP_NAME
   */
  @ApiStatus.Experimental
  interface BatchReferenceProcessingSuppressor {
    /**
     * Checks if batch reference processing should be suppressed for the given {@link PsiFile}.
     *
     * @param file the {@link PsiFile} to check
     * @return {@code true} if processing should be suppressed, {@code false} otherwise
     */
    boolean isSuppressed(@NotNull PsiFile file);

    /**
     * The extension point name for registering implementations of {@link BatchReferenceProcessingSuppressor}.
     */
    ExtensionPointName<BatchReferenceProcessingSuppressor> EP_NAME = ExtensionPointName.create("com.intellij.psi.batchReferenceProcessingSuppressor");
  }
}
