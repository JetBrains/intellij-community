// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link FileType} which is determined by the particular {@link VirtualFile}.
 * For example, a text file located in the "META-INF/services" directory should be treated as of SPI file type.
 * <p>
 * <p/>As an implementation example, see com.intellij.spi.SPIFileType
 */
public interface FileTypeIdentifiableByVirtualFile extends FileType {
  /**
   * @return true if this particular file should be treated as belonging to this file type.
   * Note that this file type can be associated with other files by other means as well (e.g., "Settings|Editor|File Types|Associate file name pattern..."),
   * so this method is just one of the possible file type definitions.
   */
  boolean isMyFileType(@NotNull VirtualFile file);

  /**
   * @return {@code true} if this file type can be used to override another file type via the "Override File Type" action.
   */
  @ApiStatus.Experimental
  default boolean isAvailableForOverride() {
    return false;
  }

  FileTypeIdentifiableByVirtualFile[] EMPTY_ARRAY = new FileTypeIdentifiableByVirtualFile[0];
  ArrayFactory<FileTypeIdentifiableByVirtualFile> ARRAY_FACTORY =
    count -> count == 0 ? EMPTY_ARRAY : new FileTypeIdentifiableByVirtualFile[count];
}