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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class FileDocumentContentImpl extends DocumentContentBase implements FileContent {
  @NotNull private final VirtualFile myFile;
  @Nullable private final VirtualFile myHighlightFile;

  public FileDocumentContentImpl(@Nullable Project project,
                                 @NotNull Document document,
                                 @NotNull VirtualFile file) {
    this(project, document, file, null);
  }

  public FileDocumentContentImpl(@Nullable Project project,
                                 @NotNull Document document,
                                 @NotNull VirtualFile file,
                                 @Nullable VirtualFile highlightFile) {
    super(project, document);
    myFile = file;
    myHighlightFile = highlightFile;
  }

  @Override
  public @Nullable VirtualFile getHighlightFile() {
    return ObjectUtils.chooseNotNull(myHighlightFile, myFile);
  }

  @Nullable
  @Override
  public LineSeparator getLineSeparator() {
    String s = LoadTextUtil.detectLineSeparator(myFile, true);
    if (s == null) return null;
    return LineSeparator.fromString(s);
  }

  @Nullable
  @Override
  public Charset getCharset() {
    return myFile.getCharset();
  }

  @Nullable
  @Override
  public Boolean hasBom() {
    return myFile.getBOM() != null;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  @Override
  public FileType getContentType() {
    return myFile.getFileType();
  }

  @Override
  public void onAssigned(boolean isAssigned) {
    if (isAssigned) DiffUtil.refreshOnFrameActivation(myFile);
  }
}
