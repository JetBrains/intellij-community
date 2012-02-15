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
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class FileElement {
  private final VirtualFile myFile;
  private final String myName;
  private Boolean myIsHidden;
  private String myPath;
  private FileElement myParent;

  public FileElement(@Nullable VirtualFile file, String name) {
    myFile = file;
    myName = name;
  }

  public void setParent(final FileElement parent) {
    myParent = parent;
  }

  public FileElement getParent() {
    return myParent;
  }

  public final VirtualFile getFile() {
    return myFile;
  }

  public final String getName() {
    return myName;
  }

  public final String getPath() {
    if (myPath == null) {
      final StringBuilder sb = StringBuilderSpinAllocator.alloc();
      try {
        FileElement element = this;
        while (element != null) {
          sb.insert(0, element.myName);
          element = element.myParent;
          if (element != null && element.myParent != null /*&& !element.myName.endsWith(File.separator)*/) {
            sb.insert(0, File.separator);
          }
        }
        myPath = sb.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(sb);
      }
    }
    return myPath;
  }

  @Override
  public int hashCode() {
    return myFile == null ? 0 : myFile.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof FileElement) {
      if (((FileElement)obj).myFile == myFile) return true;
    }
    return false;
  }

  @Override
  public final String toString() {
    return myName != null ? myName : "";
  }

  public final boolean isHidden() {
    if (myIsHidden == null) {
      myIsHidden = Boolean.valueOf(isFileHidden(getFile()));
    }
    return myIsHidden.booleanValue();
  }

  public final boolean isArchive() {
    return isArchive(getFile());
  }

  public static boolean isFileHidden(@Nullable final VirtualFile file) {
    if (file == null || !file.isValid()) return false;
    if (!file.isInLocalFileSystem()) return false;
    final File ioFile = new File(file.getPath().replace('/', File.separatorChar));
    return ioFile.isHidden() && ioFile.getParent() != null; // Under Windows logical driver files (e.g C:\) are hidden.
  }

  public static boolean isArchive(@Nullable final VirtualFile file) {
    if (file == null) return false;
    if (isArchiveFileSystem(file) && file.getParent() == null) return true;
    return !file.isDirectory() &&
           file.getFileType() == FileTypes.ARCHIVE &&
           !isArchiveFileSystem(file.getParent());
  }

  private static boolean isArchiveFileSystem(VirtualFile file) {
    return file.getFileSystem() instanceof JarFileSystem;
  }
}
