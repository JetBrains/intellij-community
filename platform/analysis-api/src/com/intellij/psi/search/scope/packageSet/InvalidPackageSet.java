// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class InvalidPackageSet extends AbstractPackageSet {

  public InvalidPackageSet(@NotNull String text) {
    super(text);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    return false;
  }
}
