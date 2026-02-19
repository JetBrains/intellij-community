// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public interface VirtualFileSetFactory {
  static VirtualFileSetFactory getInstance() {
    return ApplicationManager.getApplication().getService(VirtualFileSetFactory.class);
  }
  @NotNull
  VirtualFileSet createCompactVirtualFileSet();
  @NotNull
  VirtualFileSet createCompactVirtualFileSet(@NotNull Collection<? extends VirtualFile> files);
}
