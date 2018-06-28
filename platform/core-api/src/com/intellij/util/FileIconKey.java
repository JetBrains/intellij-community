// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
class FileIconKey {
  private final VirtualFile myFile;
  private final FileType myFileType;
  private final Project myProject;
  private final @Iconable.IconFlags int myFlags;
  private final @Nullable Language myInitialLanguage;

  FileIconKey(@NotNull VirtualFile file, FileType fileType, Project project, @Iconable.IconFlags int flags) {
    myFile = file;
    myFileType = fileType;
    myProject = project;
    myFlags = flags;
    myInitialLanguage = myFile instanceof LightVirtualFile ? ((LightVirtualFile)myFile).getLanguage() : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileIconKey)) return false;

    FileIconKey that = (FileIconKey)o;
    if (myFlags != that.myFlags) return false;
    if (!myFile.equals(that.myFile)) return false;
    if (!Objects.equals(myFileType, that.myFileType)) return false;
    if (!Objects.equals(myProject, that.myProject)) return false;

    if (!Objects.equals(myInitialLanguage, that.myInitialLanguage)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + Objects.hashCode(myFileType);
    result = 31 * result + Objects.hashCode(myProject);
    result = 31 * result + myFlags;
    return result;
  }

  VirtualFile getFile() {
    return myFile;
  }

  FileType getFileType() {
    return myFileType;
  }

  Project getProject() {
    return myProject;
  }

  @Iconable.IconFlags int getFlags() {
    return myFlags;
  }
}