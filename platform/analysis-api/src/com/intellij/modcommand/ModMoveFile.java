// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Command to rename and move files
 * 
 * @param file file to move and/or rename
 * @param targetFile target file (non-existing)
 */
public record ModMoveFile(@NotNull VirtualFile file, @NotNull FutureVirtualFile targetFile) implements ModCommand {
  @Override
  public @NotNull Set<@NotNull VirtualFile> modifiedFiles() {
    return Set.of(file);
  }
}
