// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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


  /**
   * Delivers candidate files for the given text in the given scope to the processor.
   * Candidate file is a file that _likely_ has text in it -- but it is not guaranteed.
   * (E.g. in case of trigram-index-based search engine: a candidate file is a file that contains all the trigrams of the given text
   * -- but it may not contain the actual text because e.g. trigrams could be in a different order or in different parts of the file)
   */
  @NotNull TextSearchResult processFilesWithText(@NotNull String text,
                                                 @NotNull Processor<? super VirtualFile> processor,
                                                 @NotNull GlobalSearchScope scope);

  /**
   * @return true if the file is 'covered' by this search service -- i.e. this service will search in this file during
   * {@link #processFilesWithText(String, Processor, GlobalSearchScope)} call
   */
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
