// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class IndexedFileImpl extends UserDataHolderBase implements IndexedFile {
  protected final VirtualFile myFile;
  protected final String myFileName;
  private volatile Project myProject;
  private FileType mySubstituteFileType;
  private final @NotNull FileType myType;

  public IndexedFileImpl(@NotNull VirtualFile file, Project project) {
    this(file, file.getFileType(), project);
  }

  public IndexedFileImpl(@NotNull VirtualFile file, @NotNull FileType type, Project project) {
    myFile = file;
    myFileName = file.getName();
    myProject = project;
    myType = type;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    if (mySubstituteFileType == null) {
      mySubstituteFileType = SubstitutedFileType.substituteFileType(myFile, myType, getProject());
    }
    return mySubstituteFileType;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  @Override
  public String getFileName() {
    return myFileName;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  public void setProject(Project project) {
    myProject = project;
  }

  @Override
  public String toString() {
    return "IndexedFileImpl(" + getFileName() + ")";
  }
}
