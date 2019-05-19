// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author max
 */
public class StubVirtualFile extends VirtualFile {
  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile[] getChildren() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLength() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile getParent() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getPath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimeStamp() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getUrl() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDirectory() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isValid() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isWritable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setWritable(boolean writable) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VirtualFile createChildDirectory(Object requestor, @NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VirtualFile createChildData(Object requestor, @NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(Object requestor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void move(Object requestor, @NotNull VirtualFile newParent) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VirtualFile copy(Object requestor, @NotNull VirtualFile newParent, @NotNull String copyName) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void storeCharset(Charset charset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCharset(Charset charset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCharset(Charset charset, @Nullable Runnable whenChanged) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCharset(Charset charset, @Nullable Runnable whenChanged, boolean fireEventsWhenChanged) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setBinaryContent(@NotNull byte[] content, long newModificationStamp, long newTimeStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setBinaryContent(@NotNull byte[] content, long newModificationStamp, long newTimeStamp, Object requestor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setBOM(@Nullable byte[] BOM) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDetectedLineSeparator(@Nullable String separator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPreloadedContentHint(byte[] preloadedContentHint) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> void putCopyableUserData(@NotNull Key<T> key, T value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public <T> T putUserDataIfAbsent(@NotNull Key<T> key, @NotNull T value) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void clearUserData() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void setUserMap(@NotNull KeyFMap map) {
    throw new UnsupportedOperationException();
  }
}