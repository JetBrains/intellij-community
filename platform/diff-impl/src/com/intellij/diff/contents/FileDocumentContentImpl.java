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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileDocumentContentImpl extends DocumentContentImpl implements FileContent {
  @Nullable private final Project myProject;
  @NotNull private final VirtualFile myFile;

  public FileDocumentContentImpl(@Nullable Project project,
                                 @NotNull Document document,
                                 @NotNull VirtualFile file) {
    super(document, file.getFileType(), file, getSeparator(file), file.getCharset());
    myProject = project;
    myFile = file;
  }

  @Nullable
  @Override
  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    if (myProject == null || myProject.isDefault() || !myFile.isValid()) return null;
    return new OpenFileDescriptor(myProject, myFile, offset);
  }

  @Nullable
  private static LineSeparator getSeparator(@NotNull VirtualFile file) {
    String s = LoadTextUtil.detectLineSeparator(file, true);
    if (s == null) return null;
    return LineSeparator.fromString(s);
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }
}
