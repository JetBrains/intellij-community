// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class StubVirtualFile extends VirtualFile {
  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    throw unsupported();
  }

  @Override
  public VirtualFile[] getChildren() {
    throw unsupported();
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    throw unsupported();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    throw unsupported();
  }

  @Override
  public long getLength() {
    throw unsupported();
  }

  @NotNull
  @Override
  public String getName() {
    throw unsupported();
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw unsupported();
  }

  @Override
  public VirtualFile getParent() {
    throw unsupported();
  }

  @NotNull
  @Override
  public String getPath() {
    throw unsupported();
  }

  @Override
  public long getTimeStamp() {
    throw unsupported();
  }

  @NotNull
  @Override
  public String getUrl() {
    throw unsupported();
  }

  @Override
  public boolean isDirectory() {
    throw unsupported();
  }

  @Override
  public boolean isValid() {
    throw unsupported();
  }

  @Override
  public boolean isWritable() {
    throw unsupported();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    throw unsupported();
  }

  @Override
  public void setWritable(boolean writable) {
    throw unsupported();
  }

  @NotNull
  @Override
  public VirtualFile createChildDirectory(Object requestor, @NotNull String name) {
    throw unsupported();
  }

  @NotNull
  @Override
  public VirtualFile createChildData(Object requestor, @NotNull String name) {
    throw unsupported();
  }

  @Override
  public void delete(Object requestor) {
    throw unsupported();
  }

  @Override
  public void move(Object requestor, @NotNull VirtualFile newParent) {
    throw unsupported();
  }

  @NotNull
  @Override
  public VirtualFile copy(Object requestor, @NotNull VirtualFile newParent, @NotNull String copyName) {
    throw unsupported();
  }

  @Override
  protected void storeCharset(Charset charset) {
    throw unsupported();
  }

  @Override
  public void setCharset(Charset charset) {
    throw unsupported();
  }

  @Override
  public void setCharset(Charset charset, @Nullable Runnable whenChanged) {
    throw unsupported();
  }

  @Override
  public void setCharset(Charset charset, @Nullable Runnable whenChanged, boolean fireEventsWhenChanged) {
    throw unsupported();
  }

  @Override
  public void setBinaryContent(byte @NotNull [] content, long newModificationStamp, long newTimeStamp) {
    throw unsupported();
  }

  @Override
  public void setBinaryContent(byte @NotNull [] content, long newModificationStamp, long newTimeStamp, Object requestor) {
    throw unsupported();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive) {
    throw unsupported();
  }

  @Override
  public void setBOM(byte @Nullable [] BOM) {
    throw unsupported();
  }

  @Override
  public void setDetectedLineSeparator(@Nullable String separator) {
    throw unsupported();
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    throw unsupported();
  }

  @Override
  public <T> void putCopyableUserData(@NotNull Key<T> key, T value) {
    throw unsupported();
  }

  @Override
  public <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    throw unsupported();
  }

  @NotNull
  @Override
  public <T> T putUserDataIfAbsent(@NotNull Key<T> key, @NotNull T value) {
    throw unsupported();
  }

  @Override
  protected void clearUserData() {
    throw unsupported();
  }

  @Override
  protected void setUserMap(@NotNull KeyFMap map) {
    throw unsupported();
  }

  private UnsupportedOperationException unsupported() {
    return new UnsupportedOperationException(toString());
  }
}