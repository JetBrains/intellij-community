/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.contents;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to compare files
 */
public class FileContentImpl extends DiffContentBase implements FileContent {
  @NotNull private final VirtualFile myFile;
  @Nullable private final Project myProject;
  @NotNull private final FileType myType;
  @Nullable private final VirtualFile myHighlightFile;

  public FileContentImpl(@Nullable Project project, @NotNull VirtualFile file) {
    this(project, file, file);
  }

  public FileContentImpl(@Nullable Project project,
                         @NotNull VirtualFile file,
                         @Nullable VirtualFile highlightFile) {
    assert !file.isDirectory();
    myFile = file;
    myProject = project;
    myType = file.getFileType();
    myHighlightFile = highlightFile;
  }

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    if (!DiffUtil.canNavigateToFile(myProject, myHighlightFile)) return null;
    return new OpenFileDescriptor(myProject, myHighlightFile);
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  @Override
  public FileType getContentType() {
    return myType;
  }

  @NotNull
  public String getFilePath() {
    return myFile.getPath();
  }

  @Override
  public void onAssigned(boolean isAssigned) {
    if (isAssigned) DiffUtil.refreshOnFrameActivation(myFile);
  }

  @Override
  public String toString() {
    return super.toString() + ":" + myFile;
  }
}
