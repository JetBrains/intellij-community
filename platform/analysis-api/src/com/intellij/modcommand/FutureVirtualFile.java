// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Virtual file that doesn't exist yet
 */
@ApiStatus.Experimental
public final class FutureVirtualFile extends LightVirtualFile {
  private final VirtualFile myParent;

  public FutureVirtualFile(@NotNull VirtualFile parent, @NotNull String name, @NotNull FileType fileType) {
    super(name, fileType, "");
    myParent = parent;
  }

  @Override
  public VirtualFile getParent() {
    return myParent;
  }
}
