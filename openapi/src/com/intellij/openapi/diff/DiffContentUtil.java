/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author dyoma
 */
public class DiffContentUtil {
  public static FileType getContentType(VirtualFile file) {
    if (file == null) return null;
    return FileTypeManager.getInstance().getFileTypeByFile(file);
  }

  public static boolean isTextFile(VirtualFile file) {
    return file != null && file.isValid() && !file.isDirectory() &&
           isTextType(FileTypeManager.getInstance().getFileTypeByFile(file));
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
