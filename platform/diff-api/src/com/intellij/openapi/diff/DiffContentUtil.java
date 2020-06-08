// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;

public final class DiffContentUtil {
  public static FileType getContentType(VirtualFile file) {
    if (file == null) return null;
    return file.getFileType();
  }

  public static boolean isTextFile(VirtualFile file) {
    return file != null && file.isValid() && !file.isDirectory() &&
           isTextType(file.getFileType());
  }

  public static boolean isTextType(FileType fileType) {
    return fileType != null && !fileType.isBinary();
  }

  public static String getTitle(VirtualFile virtualFile) {
    VirtualFile parent = virtualFile.getParent();
    if (parent == null) return virtualFile.getPresentableUrl();
    return virtualFile.getName() + " (" + parent.getPresentableUrl() + ")";
  }
}
