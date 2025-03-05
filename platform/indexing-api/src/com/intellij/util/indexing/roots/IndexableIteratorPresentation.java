// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

@Internal
public interface IndexableIteratorPresentation {
  /**
   * Presentable name that can be shown in logs and used for debugging purposes.
   */
  @NonNls
  String getDebugName();

  /**
   * Presentable text shown in progress indicator during indexing of files of this provider.
   */
  @Nullable
  @NlsContexts.ProgressText
  String getIndexingProgressText();

  /**
   * Presentable text shown in progress indicator during traversing of files of this provider.
   */
  @Nullable
  @NlsContexts.ProgressText
  String getRootsScanningProgressText();

  static IndexableIteratorPresentation create(@Nullable @NonNls String debugName,
                                              @Nullable @NlsContexts.ProgressText String indexingProgressText,
                                              @Nullable @NlsContexts.ProgressText String rootsScanningProgressText) {
    record Presentation(@Nullable @NonNls String debugName,
                        @Nullable @NlsContexts.ProgressText String indexingProgressText,
                        @Nullable @NlsContexts.ProgressText String rootsScanningProgressText) implements IndexableIteratorPresentation {
      @Override
      public String getDebugName() {
        return this.debugName;
      }

      @Override
      public @Nullable String getIndexingProgressText() {
        return this.indexingProgressText;
      }

      @Override
      public String getRootsScanningProgressText() {
        return this.rootsScanningProgressText;
      }
    }
    return new Presentation(debugName, indexingProgressText, rootsScanningProgressText);
  }
}
