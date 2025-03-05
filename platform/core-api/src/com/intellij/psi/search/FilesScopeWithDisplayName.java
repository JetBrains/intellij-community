// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class FilesScopeWithDisplayName extends GlobalSearchScope.FilesScope {
  private final @Nls @NotNull String myDisplayName;

  FilesScopeWithDisplayName(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files, @NotNull @Nls String displayName) {
    super(project, files, null);
    myDisplayName = displayName;
  }

  @Override
  public @NotNull String getDisplayName() {
    return myDisplayName;
  }
}
