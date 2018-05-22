/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.dvcs.hosting;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Allows to query remote service for a list of available VCS repositories with the current IDEA settings.
 * Can be used to suggest the list of repositories that can be checked out.
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

  @CalledInBackground
  @NotNull
  List<String> getAvailableRepositories(@NotNull ProgressIndicator progressIndicator) throws RepositoryListLoadingException;
}
