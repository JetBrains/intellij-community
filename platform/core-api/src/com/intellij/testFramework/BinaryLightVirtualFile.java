// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * In-memory implementation of {@link VirtualFile}.
 */
public class BinaryLightVirtualFile extends LightVirtualFileBase {
  private byte[] myContent = ArrayUtil.EMPTY_BYTE_ARRAY;

  public BinaryLightVirtualFile(String name) {
    this(name, ArrayUtil.EMPTY_BYTE_ARRAY);
  }

  public BinaryLightVirtualFile(String name, @NotNull byte[] content) {
    this(name, null, content, LocalTimeCounter.currentTime());
  }

  public BinaryLightVirtualFile(String name, FileType fileType, @NotNull byte[] content) {
    this(name, fileType, content, LocalTimeCounter.currentTime());
  }

  public BinaryLightVirtualFile(String name, FileType fileType, @NotNull byte[] content, long modificationStamp) {
    super(name, fileType, modificationStamp);
    setContent(content);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(myContent, this);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    return VfsUtilCore.outputStreamAddingBOM(new ByteArrayOutputStream() {
      @Override
      public void close() {
        setModificationStamp(newModificationStamp);
        setContent(toByteArray());
      }
    }, this);
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    return myContent;
  }

  private void setContent(@NotNull byte[] content) {
    //StringUtil.assertValidSeparators(content);
    myContent = content;
  }

  @NotNull
  public byte[] getContent() {
    return myContent;
  }

  @Override
  public String toString() {
    return "BinaryLightVirtualFile: " + getPresentableUrl();
  }
}