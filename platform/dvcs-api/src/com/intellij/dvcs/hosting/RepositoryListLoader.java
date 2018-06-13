/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.dvcs.hosting;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Allows to query remote service for a list of available VCS repositories with the current IDEA settings.
 * Can be used to suggest the list of repositories that can be checked out.
 * <p>
 * Implement either {@link #getAvailableRepositories(ProgressIndicator)} to load everything in a single request
 * or {@link #getAvailableRepositoriesFromMultipleSources(ProgressIndicator)} to load in several requests
 */
public interface RepositoryListLoader {
  /**
   * Check if this loader is configured (e.g. has necessary authentication data)
   */
  boolean isEnabled();

  /**
   * Prompt user for additional configuration (e.g. provide credentials)
   */
  boolean enable();

  /**
   * Load repository urls in a single requests
   */
  @CalledInBackground
  @NotNull
  default List<String> getAvailableRepositories(@NotNull ProgressIndicator progressIndicator) throws RepositoryListLoadingException {
    return Collections.emptyList();
  }

  /**
   * Load repository urls in multiple requests with ability to show partial result
   */
  @CalledInBackground
  @NotNull
  default Pair<List<String>, List<RepositoryListLoadingException>> getAvailableRepositoriesFromMultipleSources(@NotNull ProgressIndicator progressIndicator) {
    try {
      return Pair.create(getAvailableRepositories(progressIndicator), Collections.emptyList());
    }
    catch (RepositoryListLoadingException e) {
      return Pair.create(Collections.emptyList(), Collections.singletonList(e));
    }
  }
}
