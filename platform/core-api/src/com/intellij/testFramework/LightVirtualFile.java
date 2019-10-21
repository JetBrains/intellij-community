// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.CharsetUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;

/**
 * In-memory implementation of {@link VirtualFile}.
 */
public class LightVirtualFile extends LightVirtualFileBase {
  private CharSequence myContent;
  private Language myLanguage;

  public LightVirtualFile() {
    this("");
  }

  public LightVirtualFile(@NotNull String name) {
    this(name, "");
  }

  public LightVirtualFile(@NotNull String name, @NotNull CharSequence content) {
    this(name, null, content, LocalTimeCounter.currentTime());
  }

  public LightVirtualFile(@NotNull String name, FileType fileType, @NotNull CharSequence text) {
    this(name, fileType, text, LocalTimeCounter.currentTime());
  }

  public LightVirtualFile(VirtualFile original, @NotNull CharSequence text, long modificationStamp) {
    this(original.getName(), original.getFileType(), text, modificationStamp);
    setCharset(original.getCharset());
  }

  public LightVirtualFile(@NotNull String name, FileType fileType, @NotNull CharSequence text, long modificationStamp) {
    this(name, fileType, text, CharsetUtil.extractCharsetFromFileContent(null, null, fileType, text), modificationStamp);
  }

  public LightVirtualFile(@NotNull String name,
                          FileType fileType,
                          @NotNull CharSequence text,
                          Charset charset,
                          long modificationStamp) {
    super(name, fileType, modificationStamp);
    myContent = text;
    setCharset(charset);
  }

  public LightVirtualFile(@NotNull String name, @NotNull Language language, @NotNull CharSequence text) {
    super(name, null, LocalTimeCounter.currentTime());
    myContent = text;
    setLanguage(language);
    setCharset(CharsetToolkit.UTF8_CHARSET);
  }

  public Language getLanguage() {
    return myLanguage;
  }

  public void setLanguage(@NotNull Language language) {
    myLanguage = language;
    FileType type = language.getAssociatedFileType();
    if (type == null) {
      type = FileTypeRegistry.getInstance().getFileTypeByFileName(getNameSequence());
    }
    setFileType(type);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    assertWritable();
    return VfsUtilCore.outputStreamAddingBOM(new ByteArrayOutputStream() {
      @Override
      public void close() {
        assert isWritable();

        setModificationStamp(newModificationStamp);
        try {
          myContent = toString(getCharset().name());
        }
        catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
      }
    }, this);
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    final Charset charset = getCharset();
    final String s = getContent().toString();
    return s.getBytes(charset.name());
  }

  public void setContent(Object requestor, @NotNull CharSequence content, boolean fireEvent) {
    assertWritable();
    myContent = content;
    setModificationStamp(LocalTimeCounter.currentTime());
  }

  @NotNull
  public CharSequence getContent() {
    return myContent;
  }

  @Override
  public String toString() {
    return "LightVirtualFile: " + getPresentableUrl();
  }
}
