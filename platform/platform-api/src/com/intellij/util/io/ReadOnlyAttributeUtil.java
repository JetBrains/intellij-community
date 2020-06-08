// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public final class ReadOnlyAttributeUtil {
  /**
   * Sets specified read-only status for the spcified {@code file}.
   * This method can be performed only for files which are in local file system.
   *
   * @param file           file which read-only attribute to be changed.
   * @param readOnlyStatus new read-only status.
   * @throws IllegalArgumentException
   *                     if passed {@code file} doesn't
   *                     belong to the local file system.
   * @throws IOException if some {@code IOException} occurred.
   */
  public static void setReadOnlyAttribute(VirtualFile file, boolean readOnlyStatus) throws IOException {
    if (file.getFileSystem().isReadOnly()) {
      throw new IllegalArgumentException("Wrong file system: " + file.getFileSystem());
    }

    if (file.isWritable() == !readOnlyStatus) {
      return;
    }

    file.setWritable(!readOnlyStatus);
  }

  public static void setReadOnlyAttribute(String path, boolean readOnlyStatus) throws IOException {
    FileUtil.setReadOnlyAttribute(path, readOnlyStatus);
  }
}
