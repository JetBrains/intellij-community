// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.cacheBuilder;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The cache builder registry allows to register custom cache builders for file types which
 * are not based on a language. For language file types, the words scanner should be returned
 * from {@link com.intellij.lang.findUsages.FindUsagesProvider#getWordsScanner()}.
 */
@Internal
public abstract class CacheBuilderRegistry {
  public static CacheBuilderRegistry getInstance() {
    return ApplicationManager.getApplication().getService(CacheBuilderRegistry.class);
  }

  /**
   * Returns the cache builder registered for the specified file type.
   *
   * @param fileType the file type for which the cache builder is registered.
   * @return the cache builder, or null if none was registered.
   */
  public abstract @Nullable WordsScanner getCacheBuilder(@NotNull FileType fileType);
}
