// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Substitutes type for files which users explicitly marked with "Override File Type" action
 */
final class UserFileTypeOverrider implements FileTypeOverrider {
  /**
   * Returns {@code null} for a file without a VFS id, because {@link OverrideFileTypeManager} holds only files with a VFS id.
   * The method looks the manager up on each call, so the creation of the extension loads no persisted set.
   */
  @Override
  public @Nullable FileType getOverriddenFileType(@NotNull VirtualFile file) {
    if (!(file instanceof VirtualFileWithId)) {
      return null;
    }
    String overriddenType = OverrideFileTypeManager.getInstance().getFileValue(file);
    if (overriddenType != null) {
      return FileTypeManager.getInstance().findFileTypeByName(overriddenType);
    }
    return null;
  }
}
