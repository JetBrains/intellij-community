// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A service for fast textual content search.
 */
@ApiStatus.Experimental
public interface TextSearchService {
  static TextSearchService getInstance() {
    return ServiceManager.getService(TextSearchService.class);
  }

  @NotNull
  TextSearchResult processFilesWithText(@NotNull String text, Processor<? super VirtualFile> processor, @NotNull GlobalSearchScope scope);

  boolean isInSearchableScope(@NotNull VirtualFile file);

  enum TextSearchResult {
    FINISHED,
    STOPPED,
    NO_TRIGRAMS
  }
}
