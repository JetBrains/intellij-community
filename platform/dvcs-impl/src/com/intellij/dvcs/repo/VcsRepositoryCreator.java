// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates {@link Repository} instance for appropriate vcs if root is valid
 */
public interface VcsRepositoryCreator {
  @Nullable Repository createRepositoryIfValid(@NotNull Project project, @NotNull VirtualFile root, @NotNull Disposable parentDisposable);

  @NotNull VcsKey getVcsKey();
}
