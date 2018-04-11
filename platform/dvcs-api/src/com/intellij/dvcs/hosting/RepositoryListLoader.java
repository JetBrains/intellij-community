/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.dvcs.hosting;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface RepositoryListLoader {
  boolean isEnabled();

  boolean enable();

  @CalledInBackground
  @NotNull
  List<String> getAvailableRepositories(@NotNull ProgressIndicator progressIndicator) throws RepositoryListLoadingException;
}
