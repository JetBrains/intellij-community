// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class IndexedFileImpl extends UserDataHolderBase implements IndexedFile {
  protected final VirtualFile myFile;
  protected final String myFileName;
  protected final FileType myFileType;

  public IndexedFileImpl(@NotNull VirtualFile file, @NotNull FileType type) {
    myFile = file;
    myFileName = file.getName();
    myFileType = type;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return SubstitutedFileType.substituteFileType(myFile, myFileType, getProject());
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
    return getUserData(IndexingDataKeys.PROJECT);
  }
}
