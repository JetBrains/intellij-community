// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link FileType} which is determined by the particular {@link VirtualFile} rather than by its name.
 * <p>
 * This is the way to go when a raw file name is not enough to tell the file type,
 * and the decision requires knowing the full path or some other, trickier condition. For example:
 * <ul>
 *   <li>the location matters: an arbitrarily named text file is of the SPI file type only when it is located in a {@code META-INF/services} directory;</li>
 *   <li>the surroundings matter: a {@code *.metadata.json} file is Angular metadata only when a matching {@code *.d.ts} file sits next to it;</li>
 *   <li>the set of names is not known upfront: it comes from user settings, an installed bundle, or the project model.</li>
 * </ul>
 *
 * <p>
 * DO NOT USE this interface if your file type can be figured out by a raw file name.
 * Use {@link com.intellij.openapi.fileTypes.impl.FileTypeBean#fileNames} or {@link com.intellij.openapi.fileTypes.impl.FileTypeBean#patterns}
 *
 * <p>
 * <i>
 * N.B. Please use with extreme caution.
 * Since this is a code-only approach to detecting a file type,
 * it's impossible to say upfront that exactly files are affected, and thus it's easy to break other file types.
 * If possible, pattern-match your file types via {@link com.intellij.openapi.fileTypes.FileTypeManager#associate} instead.
 * </i>
 * </p>
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