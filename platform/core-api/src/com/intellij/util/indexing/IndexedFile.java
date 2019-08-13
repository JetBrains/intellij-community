// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface IndexedFile extends UserDataHolder {
  @NotNull
  FileType getFileType();

  @NotNull
  VirtualFile getFile();

  @NotNull
  String getFileName();

  Project getProject();
}
