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
package com.intellij.openapi.diff;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.pom.Navigatable;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;

/**
 * A {@link DiffContent} represented as a byte array. It still contain a text though.
 */
public class BinaryContent extends DiffContent {
  private final Project myProject;
  private final byte[] myBytes;
  private final Charset myCharset;
  private final FileType myFileType;
  private final String myFilePath;
  private Document myDocument = null;

  /**
   * @param charset use to convert bytes to String. null means bytes can't be converted to text. Has no sense if fileType.isBinary()
   * @param fileType type of content
   */
  public BinaryContent(@NotNull Project project, byte[] bytes, @Nullable Charset charset, @NotNull FileType fileType, @Nullable String filePath) {
    myProject = project;
    myBytes = bytes;
    myCharset = fileType.isBinary() ? null : charset;
    myFileType = fileType;
    myFilePath = filePath;
  }

  @Override
  @Nullable
  public Document getDocument() {
    if (myDocument == null) {
      if (isBinary()) return null;

      String text = null;
      try {
        Charset charset = ObjectUtils.notNull(myCharset, EncodingProjectManager.getInstance(myProject).getDefaultCharset());
        text = CharsetToolkit.bytesToString(myBytes, charset);
      }
      catch (IllegalCharsetNameException ignored) { }

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
  public Navigatable getOpenFileDescriptor(int offset) {
    VirtualFile file = findVirtualFile();
    return file == null ? null : PsiNavigationSupport.getInstance().createNavigatable(myProject, file, offset);
  }

  @Nullable
  private VirtualFile findVirtualFile() {
    return myFilePath != null ? LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(myFilePath)) : null;
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
