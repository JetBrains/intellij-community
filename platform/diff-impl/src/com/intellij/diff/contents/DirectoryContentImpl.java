// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.contents;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class DirectoryContentImpl extends DiffContentBase implements DirectoryContent {
  private final @NotNull VirtualFile myFile;
  private final @Nullable Project myProject;
  private final @Nullable VirtualFile myHighlightFile;

  public DirectoryContentImpl(@Nullable Project project, @NotNull VirtualFile file) {
    this(project, file, file);
  }

  public DirectoryContentImpl(@Nullable Project project, @NotNull VirtualFile file, @Nullable VirtualFile highlightFile) {
    assert file.isValid() && file.isDirectory();
    myProject = project;
    myFile = file;
    myHighlightFile = highlightFile;
  }

  @Override
  public @Nullable Navigatable getNavigatable() {
    if (!DiffUtil.canNavigateToFile(myProject, myHighlightFile)) return null;
    return new OpenFileDescriptor(myProject, myHighlightFile);
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public @Nullable FileType getContentType() {
    return null;
  }

  @Override
  public String toString() {
    return super.toString() + ":" + myFile;
  }
}
