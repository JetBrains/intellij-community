// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Initializes a Git repository in the specified place.
 *
 * @see GitSilentFileAdderProvider#create(Project)
 */
public interface GitRepositoryInitializer {
  ExtensionPointName<GitRepositoryInitializer> EP_NAME = ExtensionPointName.create("com.intellij.gitRepositoryInitializer");

  @RequiresBackgroundThread
  default void initRepository(@NotNull Project project, @NotNull VirtualFile root) {
    initRepository(project, root, false);
  }

  @RequiresBackgroundThread
  void initRepository(@NotNull Project project, @NotNull VirtualFile root, boolean addFilesToVcs);

  @Nullable
  static GitRepositoryInitializer getInstance() {
    return EP_NAME.extensions().findFirst().orElse(null);
  }
}
