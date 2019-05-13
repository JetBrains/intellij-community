/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class StubVirtualFile extends VirtualFile {
  @Override
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile[] getChildren() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
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

  @Override
  @NotNull
  @NonNls
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, final long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public VirtualFile getParent() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public String getPath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimeStamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
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
  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
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