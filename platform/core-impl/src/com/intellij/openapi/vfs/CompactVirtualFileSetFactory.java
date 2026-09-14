// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * The factory of the IDE and of the core environments, see {@code com.intellij.core.CoreApplicationEnvironment}.
 */
@ApiStatus.Internal
public final class CompactVirtualFileSetFactory implements VirtualFileSetFactory {
  @Override
  public @NotNull VirtualFileSet createCompactVirtualFileSet() {
    return new CompactVirtualFileSet();
  }

  @Override
  public @NotNull VirtualFileSet createCompactVirtualFileSet(@NotNull Collection<? extends VirtualFile> files) {
    return new CompactVirtualFileSet(files);
  }
}
