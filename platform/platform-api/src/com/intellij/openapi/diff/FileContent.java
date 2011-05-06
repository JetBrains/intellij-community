/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileContent extends DiffContent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.FileContent");
  private final VirtualFile myFile;
  private Document myDocument;
  private final Project myProject;

  public FileContent(Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
  }

  public Document getDocument() {
    if (myDocument == null && DiffContentUtil.isTextFile(myFile))
      myDocument = FileDocumentManager.getInstance().getDocument(myFile);
    return myDocument;
  }

  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    return new OpenFileDescriptor(myProject, myFile, offset);
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public FileType getContentType() {
    return DiffContentUtil.getContentType(myFile);
  }

  public byte[] getBytes() throws IOException {
    if (myFile.isDirectory()) return null;
    return myFile.contentsToByteArray();
  }

  public boolean isBinary() {
    if (myFile.isDirectory()) return false;
    return FileTypeManager.getInstance().getFileTypeByFile(myFile).isBinary();
  }

  @Nullable
  public static FileContent createFromTempFile(Project project, String name, String ext, byte[] content) {
    try {
      final File tempFile = FileUtil.createTempFile(name, ext);
      tempFile.deleteOnExit();
      final FileOutputStream fos = new FileOutputStream(tempFile);
      fos.write(content);
      fos.close();
      final VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(tempFile);
      if (file != null) {
        return new FileContent(project, file);
      }
    }
    catch (IOException e) {//
    }
    return null;
  }
}
