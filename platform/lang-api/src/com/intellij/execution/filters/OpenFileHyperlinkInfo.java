// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenFileHyperlinkInfo extends FileHyperlinkInfoBase implements FileHyperlinkInfo {

  private final VirtualFile myFile;

  public OpenFileHyperlinkInfo(@NotNull OpenFileDescriptor descriptor) {
    this(descriptor.getProject(), descriptor.getFile(), descriptor.getLine(), descriptor.getColumn());
  }

  public OpenFileHyperlinkInfo(@NotNull Project project, @NotNull VirtualFile file, int documentLine, int documentColumn) {
    super(project, documentLine, documentColumn);
    myFile = file;
  }

  public OpenFileHyperlinkInfo(@NotNull Project project, @NotNull VirtualFile file, int documentLine, int documentColumn, boolean isUseBrowser) {
    super(project, documentLine, documentColumn, isUseBrowser);
    myFile = file;
  }

  public OpenFileHyperlinkInfo(@NotNull Project project, final @NotNull VirtualFile file, final int line) {
    this(project, file, line, 0);
  }

  @Override
  public @Nullable VirtualFile getVirtualFile() {
    return myFile;
  }
}
