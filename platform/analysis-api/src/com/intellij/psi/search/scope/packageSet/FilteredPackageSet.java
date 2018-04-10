// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FilteredPackageSet extends AbstractPackageSet {
  public FilteredPackageSet(@NotNull String text) {
    super(text);
  }

  public FilteredPackageSet(@NotNull String text, int priority) {
    super(text, priority);
  }

  public abstract boolean contains(@NotNull VirtualFile file, @NotNull Project project);

  @Override
  public boolean contains(VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    return file != null && contains(file, project);
  }

  @Override
  public boolean contains(VirtualFile file, NamedScopesHolder holder) {
    return file != null && holder != null && contains(file, holder.getProject(), holder);
  }
}
