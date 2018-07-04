/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class FileElement {
  private final VirtualFile myFile;
  private final String myName;
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

  @NotNull
  public final String getPath() {
    if (myPath == null) {
      final StringBuilder sb = new StringBuilder();
      FileElement element = this;
      while (element != null) {
        if (element.myParent != null || !element.myName.equals(File.separator)) {
          sb.insert(0, element.myName);
        }
        element = element.myParent;
        if (element != null) {
          sb.insert(0, File.separator);
        }
      }
      myPath = sb.toString();
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
      if (Comparing.equal(((FileElement)obj).myFile, myFile)) return true;
    }
    return false;
  }

  @Override
  public final String toString() {
    return myName != null ? myName : "";
  }

  public final boolean isHidden() {
    return isFileHidden(myFile);
  }

  public final boolean isArchive() {
    return isArchive(getFile());
  }

  public static boolean isFileHidden(@Nullable VirtualFile file) {
    return file != null &&
           file.isValid() &&
           file.isInLocalFileSystem() &&
           (file.is(VFileProperty.HIDDEN) || SystemInfo.isUnix && file.getName().startsWith("."));
  }

  public static boolean isArchive(@Nullable VirtualFile file) {
    if (file == null) return false;
    if (isArchiveFileSystem(file) && file.getParent() == null) return true;
    return !file.isDirectory() &&
           FileTypeManager.getInstance().getFileTypeByFileName(file.getNameSequence()) == FileTypes.ARCHIVE &&
           !isArchiveFileSystem(file.getParent());
  }

  private static boolean isArchiveFileSystem(VirtualFile file) {
    return file.getFileSystem() instanceof JarFileSystem;
  }
}
