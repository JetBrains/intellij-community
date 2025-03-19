// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
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
    return ApplicationManager.getApplication().getService(TextSearchService.class);
  }

  @NotNull
  TextSearchResult processFilesWithText(@NotNull String text,
                                        @NotNull Processor<? super VirtualFile> processor,
                                        @NotNull GlobalSearchScope scope);

  boolean isInSearchableScope(@NotNull VirtualFile file,
                              @NotNull Project project);

  enum TextSearchResult {
    /** The search has finished successfully */
    FINISHED,

    /** The search has been aborted */
    STOPPED,

    /** No trigrams are extracted from the provided text. Thus, the search has not been run */
    NO_TRIGRAMS
  }
}
