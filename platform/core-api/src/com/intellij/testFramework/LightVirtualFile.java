// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.CharsetUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * In-memory implementation of {@link VirtualFile}.
 */
public class LightVirtualFile extends LightVirtualFileBase {
  private CharSequence myContent;
  private Language myLanguage;

  public LightVirtualFile() {
    this("");
  }

  public LightVirtualFile(@NlsSafe @NotNull String name) {
    this(name, "");
  }

  public LightVirtualFile(@NlsSafe @NotNull String name, @NotNull CharSequence content) {
    this(name, null, content, LocalTimeCounter.currentTime());
  }

  public LightVirtualFile(@NlsSafe @NotNull String name, FileType fileType, @NotNull CharSequence text) {
    this(name, fileType, text, LocalTimeCounter.currentTime());
  }

  public LightVirtualFile(VirtualFile original, @NotNull CharSequence text, long modificationStamp) {
    this(original.getName(), original.getFileType(), text, modificationStamp);
    setCharset(original.getCharset());
  }

  public LightVirtualFile(@NlsSafe @NotNull String name, @Nullable FileType fileType, @NotNull CharSequence text, long modificationStamp) {
    this(name, fileType, text, CharsetUtil.extractCharsetFromFileContent(null, null, fileType, text), modificationStamp);
  }

  public LightVirtualFile(@NlsSafe @NotNull String name,
                          @Nullable FileType fileType,
                          @NlsSafe @NotNull CharSequence text,
                          Charset charset,
                          long modificationStamp) {
    super(name, fileType, modificationStamp);
    myContent = text;
    setCharset(charset);
  }

  public LightVirtualFile(@NlsSafe @NotNull String name, @NotNull Language language, @NlsSafe @NotNull CharSequence text) {
    super(name, null, LocalTimeCounter.currentTime());
    myContent = text;
    setLanguage(language);
    setCharset(StandardCharsets.UTF_8);
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
  public @NotNull InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this);
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
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
  public byte @NotNull [] contentsToByteArray() throws IOException {
    final Charset charset = getCharset();
    final String s = getContent().toString();
    return s.getBytes(charset);
  }

  public void setContent(Object requestor, @NotNull CharSequence content, boolean fireEvent) {
    assertWritable();
    myContent = content;
    setModificationStamp(LocalTimeCounter.currentTime());
  }

  public @NotNull CharSequence getContent() {
    return myContent;
  }

  public @NotNull ThreeState isTooLargeForIntelligence() {
    return ThreeState.UNSURE;
  }

  @Override
  public String toString() {
    return "LightVirtualFile: " + getPresentableUrl();
  }
}
