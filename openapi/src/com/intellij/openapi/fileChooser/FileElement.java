/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class FileElement {
  private final VirtualFile myFile;
  private final String myName;
  private Boolean myIsHidden;

  public FileElement(VirtualFile file, String name) {
    myFile = file;
    myName = name;
  }

  public int hashCode() {
    return myFile == null ? 0 : myFile.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj instanceof FileElement) {
      if (((FileElement)obj).myFile == myFile) return true;
    }
    return false;
  }

  public final boolean isHidden() {
    if (myIsHidden == null) {
      myIsHidden = Boolean.valueOf(isFileHidden(getFile()));
    }
    return myIsHidden.booleanValue();
  }

  public final VirtualFile getFile() {
    return myFile;
  }

  public final String toString() {
    if (myName != null) {
      return myName;
    }
    return myFile.getName();
  }

  public final String getName() { return myName; }

  public static boolean isFileHidden(VirtualFile virtualFile) {
    if (virtualFile == null || !virtualFile.isValid()) return false;
    if (!(virtualFile.getFileSystem() instanceof LocalFileSystem)) return false;
    File file = new File(virtualFile.getPath().replace('/', File.separatorChar));
    return file.getParent() != null && file.isHidden(); // Under Windows logical driver files (e.g C:\) are hidden.
  }

  public static boolean isArchive(VirtualFile file) {
    if (isArchiveFileSystem(file) && file.getParent() == null) return true;
    return !file.isDirectory() &&
           FileTypeManager.getInstance().getFileTypeByFile(file) == StdFileTypes.ARCHIVE &&
           !isArchiveFileSystem(file.getParent());
  }

  private static boolean isArchiveFileSystem(VirtualFile file) {
    return file.getFileSystem() instanceof JarFileSystem;
  }

  public boolean isArchive() {
    return isArchive(getFile());
  }
}
