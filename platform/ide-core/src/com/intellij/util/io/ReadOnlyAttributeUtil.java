// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Path;

public final class ReadOnlyAttributeUtil {
  /**
   * Sets specified read-only status for the specified {@code file}.
   * This method can be performed only for files residing in the local file system.
   *
   * @param file           file which read-only attribute to be changed.
   * @param readOnlyStatus new read-only status.
   * @throws IllegalArgumentException if passed {@code file} doesn't belong to the local file system.
   * @throws IOException              if some {@code IOException} occurred.
   */
  public static void setReadOnlyAttribute(VirtualFile file, boolean readOnlyStatus) throws IOException {
    if (file.getFileSystem().isReadOnly()) {
      throw new IllegalArgumentException("Wrong file system: " + file.getFileSystem());
    }

    boolean writable = !readOnlyStatus;
    if (file.isWritable() != writable) {
      file.setWritable(writable);
    }
  }

  /** @deprecated use {@link NioFiles#setReadOnly} */
  @Deprecated(forRemoval = true)
  public static void setReadOnlyAttribute(String path, boolean readOnlyStatus) throws IOException {
    NioFiles.setReadOnly(Path.of(path), readOnlyStatus);
  }
}
