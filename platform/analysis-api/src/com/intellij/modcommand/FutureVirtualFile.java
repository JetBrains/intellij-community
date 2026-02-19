// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Virtual file that doesn't exist yet.
 */
@ApiStatus.Experimental
public final class FutureVirtualFile extends LightVirtualFile {
  private final VirtualFile myParent;
  private final boolean myDirectory;

  /**
   * @param parent parent file (may also be a future file)
   * @param name file name
   * @param fileType file type (null = directory)
   */
  public FutureVirtualFile(@NotNull VirtualFile parent, @NotNull String name, @Nullable FileType fileType) {
    super(name, fileType, "");
    myParent = parent;
    myDirectory = fileType == null;
  }

  @Override
  public boolean isDirectory() {
    return myDirectory;
  }

  @Override
  public VirtualFile getParent() {
    return myParent;
  }
}
