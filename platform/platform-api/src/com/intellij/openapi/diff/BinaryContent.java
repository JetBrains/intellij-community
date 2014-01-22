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
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;

/**
 * A {@link DiffContent} represented as a byte array. It still contain a text though.
 */
public class BinaryContent extends DiffContent {
  @NotNull private final Project myProject;
  @NotNull
  private final FileType myFileType;
  private final byte[] myBytes;
  private final Charset myCharset;
  private Document myDocument = null;
  private String myFilePath;

  /**
   * @param charset use to convert bytes to String. null means bytes can't be converted to text. Has no sense if fileType.isBinary()
   * @param fileType type of content
   */
  public BinaryContent(@NotNull Project project, byte[] bytes, @Nullable Charset charset, @NotNull FileType fileType,
                       @Nullable String filePath) {
    myProject = project;
    myFileType = fileType;
    myBytes = bytes;
    if (fileType.isBinary()) {
      myCharset = null;
    }
    else {
      myCharset = charset;
    }
    myFilePath = filePath;
  }

  /**
   * @deprecated to remove in IDEA 14. Use {@link #BinaryContent(Project, byte[], Charset, FileType, String)}.
   */
  public BinaryContent(byte[] bytes, Charset charset, @NotNull FileType fileType) {
    this(bytes, charset, fileType, null);
  }
  
  /**
   * @deprecated to remove in IDEA 14. Use {@link #BinaryContent(Project, byte[], Charset, FileType, String)}.
   */
  public BinaryContent(byte[] bytes, Charset charset, @NotNull FileType fileType, String filePath) {
    this(ProjectManager.getInstance().getDefaultProject(), bytes, charset, fileType, filePath);
  }

  @Override
  @SuppressWarnings({"EmptyCatchBlock"})
  @Nullable
  public Document getDocument() {
    if (myDocument == null) {
      if (isBinary()) return null;

      String text = null;
      try {
        if (myCharset == null) {
          text = CharsetToolkit.bytesToString(myBytes, EncodingRegistry.getInstance().getDefaultCharset());
        }
        else {
          text = CharsetToolkit.bytesToString(myBytes, myCharset);
        }
      }
      catch (IllegalCharsetNameException e) {
      }

      //  Still NULL? only if not supported or an exception was thrown.
      //  Decode a string using the truly default encoding.
      if (text == null) text = new String(myBytes);
      text = LineTokenizer.correctLineSeparators(text);

      myDocument = EditorFactory.getInstance().createDocument(text);
      myDocument.setReadOnly(true);
    }
    return myDocument;
  }

  @Override
  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    VirtualFile file = findVirtualFile();
    return file == null ? null : new OpenFileDescriptor(myProject, file, offset);
  }

  @Nullable
  private VirtualFile findVirtualFile() {
    return LocalFileSystem.getInstance().findFileByIoFile(new File(myFilePath));
  }

  @Override
  @Nullable
  public VirtualFile getFile() {
    if (myFileType instanceof UIBasedFileType) {
      final VirtualFile file = findVirtualFile();
      if (file != null) {
        final LightVirtualFile lightFile = new LightVirtualFile(file, new String(myBytes), 1);
        lightFile.setOriginalFile(file);
        return lightFile;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public FileType getContentType() {
    return myFileType;
  }

  @Override
  public byte[] getBytes() throws IOException {
    return myBytes;
  }

  @Override
  public boolean isBinary() {
    return myCharset == null;
  }
}
