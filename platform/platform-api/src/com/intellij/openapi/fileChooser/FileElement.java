/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class FileElement {
  private final VirtualFile myFile;
  private final String myName;
  private Boolean myIsHidden;

  private FileElement myParent;

  public FileElement(VirtualFile file, String name) {
    myFile = file;
    myName = name;
  }

  public void setParent(final FileElement parent) {
    myParent = parent;
  }

  public FileElement getParent() {
    return myParent;
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
    else if (myFile != null) {
      return myFile.getName();
    }
    return "";
  }

  public final String getName() { return myName; }

  public static boolean isFileHidden(VirtualFile virtualFile) {
    if (virtualFile == null || !virtualFile.isValid()) return false;
    if (!virtualFile.isInLocalFileSystem()) return false;
    File file = new File(virtualFile.getPath().replace('/', File.separatorChar));
    return file.getParent() != null && file.isHidden(); // Under Windows logical driver files (e.g C:\) are hidden.
  }

  public static boolean isArchive(VirtualFile file) {
    if (isArchiveFileSystem(file) && file.getParent() == null) return true;
    return !file.isDirectory() &&
           file.getFileType() == FileTypes.ARCHIVE &&
           !isArchiveFileSystem(file.getParent());
  }

  private static boolean isArchiveFileSystem(VirtualFile file) {
    return file.getFileSystem() instanceof JarFileSystem;
  }

  public boolean isArchive() {
    return isArchive(getFile());
  }
}
