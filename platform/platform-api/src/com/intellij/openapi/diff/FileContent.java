/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class FileContent extends DiffContent {
  @NotNull private final VirtualFile myFile;
  private Document myDocument;
  private final Project myProject;
  @Nullable private final FileType myType;

  public FileContent(Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    myType = detectType(file);
  }

  @Override
  public Document getDocument() {
    if (myDocument == null && DiffContentUtil.isTextFile(myFile)) {
      myDocument = FileDocumentManager.getInstance().getDocument(myFile);
    }
    return myDocument;
  }

  @Override
  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    return new OpenFileDescriptor(myProject, myFile, offset);
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  @Nullable
  public FileType getContentType() {
    FileType type = myFile.getFileType();
    return isUnknown(type) ? myType : type;
  }

  @Override
  public byte[] getBytes() throws IOException {
    if (myFile.isDirectory()) return null;
    return myFile.contentsToByteArray();
  }

  @Override
  public boolean isBinary() {
    if (myFile.isDirectory()) return false;
    if (myType != null && !myType.isBinary()) {
      return false;
    }
    return myFile.getFileType().isBinary();
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

  @Nullable
  private static FileType detectType(@NotNull VirtualFile file) {
    FileType type = FileTypeManager.getInstance().getFileTypeByFile(file);
    if (isUnknown(type)) {
      type = FileTypeManager.getInstance().detectFileTypeFromContent(file);
    }
    // the type is left null intentionally: according to the contract of #getContentType,
    // if file type is null it may be taken from another diff content
    return isUnknown(type) ? null : type;
  }

  private static boolean isUnknown(@NotNull FileType type) {
    return type.equals(UnknownFileType.INSTANCE);
  }

  @NotNull
  @Override
  public LineSeparator getLineSeparator() {
    return LineSeparator.fromString(FileDocumentManager.getInstance().getLineSeparator(myFile, myProject));
  }

}
