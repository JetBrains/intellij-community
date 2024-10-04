// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.CharsetUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Contract;
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
  private long myCachedLength = Long.MIN_VALUE;

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

  public LightVirtualFile(@NotNull VirtualFile original, @NotNull CharSequence text, long modificationStamp) {
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
    setContentImpl(text);
    setCharset(charset);
  }

  public LightVirtualFile(@NlsSafe @NotNull String name, @NotNull Language language, @NlsSafe @NotNull CharSequence text) {
    super(name, null, LocalTimeCounter.currentTime());
    setContentImpl(text);
    setLanguage(language);
    setCharset(StandardCharsets.UTF_8);
  }

  @Override
  protected void storeCharset(Charset charset) {
    super.storeCharset(charset);
    myCachedLength = Long.MIN_VALUE;
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
    return VfsUtilCore.byteStreamSkippingBOM(doGetContent(), this);
  }

  @Override
  public long getLength() {
    long cachedLength = myCachedLength;
    if (cachedLength == Long.MIN_VALUE) {
      myCachedLength = cachedLength = super.getLength();
    }
    return cachedLength;
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
          setContentImpl(toString(getCharset().name()));
        }
        catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
      }
    }, this);
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    long cachedLength = myCachedLength;
    if (FileSizeLimit.isTooLarge(cachedLength, FileUtilRt.getExtension(getNameSequence()).toString())) {
      throw new FileTooBigException("file too big, length = "+cachedLength);
    }
    return doGetContent();
  }

  private byte @NotNull [] doGetContent() {
    Charset charset = getCharset();
    String s = getContent().toString();
    byte[] result = s.getBytes(charset);
    byte[] bom = getBOM();
    return bom == null ? result : ArrayUtil.mergeArrays(bom, result);
  }

  public void setContent(Object requestor, @NotNull CharSequence content, boolean fireEvent) {
    assertWritable();
    setContentImpl(content);
    setModificationStamp(LocalTimeCounter.currentTime());
  }

  private void setContentImpl(@NotNull CharSequence content) {
    myContent = content;
    myCachedLength = Long.MIN_VALUE;
  }

  public @NotNull CharSequence getContent() {
    return myContent;
  }

  public @NotNull ThreeState isTooLargeForIntelligence() {
    return ThreeState.UNSURE;
  }

  /**
   * @return true if this virtual file is considered a non-physical,
   * and changes in the file should not produce events and
   * can be performed outside of write action.
   */
  public boolean shouldSkipEventSystem() {
    return false;
  } 

  @Override
  public String toString() {
    return "LightVirtualFile: " + getPresentableUrl();
  }

  /**
   * Determines if the given virtual file should be treated as non-physical one
   *
   * @param virtualFile the virtual file to check
   * @return true if the virtual file is an instance of LightVirtualFile and {@link #shouldSkipEventSystem()} method returns true,
   * false otherwise
   */
  @Contract("null -> false")
  public static boolean shouldSkipEventSystem(@Nullable VirtualFile virtualFile) {
    return virtualFile instanceof LightVirtualFile && ((LightVirtualFile)virtualFile).shouldSkipEventSystem();
  }
}
