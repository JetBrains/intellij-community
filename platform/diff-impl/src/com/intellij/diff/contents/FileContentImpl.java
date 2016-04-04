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

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to compare files
 */
public class FileContentImpl extends DiffContentBase implements FileContent {
  @NotNull private final VirtualFile myFile;
  @Nullable private final Project myProject;
  @NotNull private final FileType myType;

  public FileContentImpl(@Nullable Project project, @NotNull VirtualFile file) {
    assert file.isValid() && !file.isDirectory();
    myProject = project;
    myFile = file;
    myType = file.getFileType();
  }

  @Nullable
  @Override
  public OpenFileDescriptor getOpenFileDescriptor() {
    if (myProject == null || myProject.isDefault() || !myFile.isValid()) return null;
    return new OpenFileDescriptor(myProject, myFile);
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
}
