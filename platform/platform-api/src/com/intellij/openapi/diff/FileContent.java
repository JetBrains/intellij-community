/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class FileContent extends DiffContent {
  @NotNull private final VirtualFile myFile;
  private Document myDocument;
  private final Project myProject;
  private final FileType myType;

  public FileContent(Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    myType = file.getFileType();
  }

  @Override
  public Document getDocument() {
    if (myDocument == null && DiffContentUtil.isTextFile(myFile)) {
      myDocument = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(myFile));
    }
    return myDocument;
  }

  @Override
  public Navigatable getOpenFileDescriptor(int offset) {
    return PsiNavigationSupport.getInstance().createNavigatable(myProject, myFile, offset);
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  @Nullable
  public FileType getContentType() {
    return myType;
  }

  @Override
  public byte[] getBytes() throws IOException {
    if (myFile.isDirectory()) return null;
    return myFile.contentsToByteArray();
  }

  @Override
  public boolean isBinary() {
    return !myFile.isDirectory() && myType.isBinary();
  }

  public static FileContent createFromTempFile(Project project, String name, String ext, @NotNull byte[] content) throws IOException {
    File tempFile = FileUtil.createTempFile(name, "." + ext);
    if (content.length != 0) {
      FileUtil.writeToFile(tempFile, content);
    }
    tempFile.deleteOnExit();
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile file = lfs.findFileByIoFile(tempFile);
    if (file == null) {
      file = lfs.refreshAndFindFileByIoFile(tempFile);
    }
    if (file != null) {
      return new FileContent(project, file);
    }
    throw new IOException("Can not create temp file for revision content");
  }

  @NotNull
  @Override
  public LineSeparator getLineSeparator() {
    return LineSeparator.fromString(FileDocumentManager.getInstance().getLineSeparator(myFile, myProject));
  }

}
