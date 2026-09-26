// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

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
                                              @Nullable @NlsContexts.ProgressText Supplier<String> indexingProgressText,
                                              @Nullable @NlsContexts.ProgressText Supplier<String> rootsScanningProgressText) {
    record Presentation(@Nullable @NonNls String debugName,
                        @Nullable Supplier<@NlsContexts.ProgressText String> indexingProgressText,
                        @Nullable Supplier<@NlsContexts.ProgressText String> rootsScanningProgressText) implements IndexableIteratorPresentation {
      @Override
      public String getDebugName() {
        return this.debugName;
      }

      @Override
      public @Nullable String getIndexingProgressText() {
        if (this.indexingProgressText == null) return null;

        return this.indexingProgressText.get();
      }

      @Override
      public String getRootsScanningProgressText() {
        if (this.rootsScanningProgressText == null) return null;

        return this.rootsScanningProgressText.get();
      }
    }
    return new Presentation(debugName, indexingProgressText, rootsScanningProgressText);
  }
}
