/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

  public OpenFileHyperlinkInfo(@NotNull Project project, @NotNull final VirtualFile file, final int line) {
    this(project, file, line, 0);
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    return myFile;
  }
}
