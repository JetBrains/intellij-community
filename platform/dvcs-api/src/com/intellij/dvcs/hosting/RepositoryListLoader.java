/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.dvcs.hosting;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Allows to query remote service for a list of available VCS repositories with the current IDEA settings.
 * Can be used to suggest the list of repositories that can be checked out.
 * <p>
 * Implement either {@link #getAvailableRepositories(ProgressIndicator)} to load everything in a single request
 * or {@link #getAvailableRepositoriesFromMultipleSources(ProgressIndicator)} to load in several requests
 *
 * @deprecated deprecated with the removal of completion from an old clone dialog
 */
@Deprecated
public interface RepositoryListLoader {
  /**
   * Check if this loader is configured (e.g. has necessary authentication data)
   */
  boolean isEnabled();

  /**
   * Prompt user for additional configuration (e.g. provide credentials)
   */
  default boolean enable(@Nullable Component parentComponent) {
    return enable();
  }

  /**
   * @deprecated parent component is required for dialogs to not fall through on welcome screen
   */
  @Deprecated(forRemoval = true)
  default boolean enable() { return false; }

  /**
   * Load repository urls in a single requests
   */
  @RequiresBackgroundThread
  @NotNull
  default List<String> getAvailableRepositories(@NotNull ProgressIndicator progressIndicator) throws RepositoryListLoadingException {
    return Collections.emptyList();
  }

  /**
   * Load repository urls in multiple requests with ability to show partial result
   */
  @RequiresBackgroundThread
  @NotNull
  default Result getAvailableRepositoriesFromMultipleSources(@NotNull ProgressIndicator progressIndicator) {
    try {
      return new Result(getAvailableRepositories(progressIndicator), Collections.emptyList());
    }
    catch (RepositoryListLoadingException e) {
      return new Result(Collections.emptyList(), Collections.singletonList(e));
    }
  }

  /**
   * Result from multiple sources
   */
  class Result {
    @NotNull private final List<String> myUrls;
    @NotNull private final List<RepositoryListLoadingException> myErrors;

    public Result(@NotNull List<String> urls, @NotNull List<RepositoryListLoadingException> errors) {
      this.myUrls = urls;
      this.myErrors = errors;
    }

    /**
     * @return all loaded urls (can contain duplicates)
     */
    @NotNull
    public List<String> getUrls() {
      return myUrls;
    }

    /**
     * @return exceptions occurred during loading from some sources
     */
    @NotNull
    public List<RepositoryListLoadingException> getErrors() {
      return myErrors;
    }
  }
}
