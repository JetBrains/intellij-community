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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StubVirtualFile extends VirtualFile {
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    throw new UnsupportedOperationException("contentsToByteArray is not implemented");
  }

  public VirtualFile[] getChildren() {
    throw new UnsupportedOperationException("getChildren is not implemented");
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    throw new UnsupportedOperationException("getFileSystem is not implemented");
  }

  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException("getInputStream is not implemented");
  }

  public long getLength() {
    throw new UnsupportedOperationException("getLength is not implemented");
  }

  @NotNull
  @NonNls
  public String getName() {
    throw new UnsupportedOperationException("getName is not implemented");
  }

  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, final long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException("getOutputStream is not implemented");
  }

  @Nullable
  public VirtualFile getParent() {
    throw new UnsupportedOperationException("getParent is not implemented");
  }

  public String getPath() {
    throw new UnsupportedOperationException("getPath is not implemented");
  }

  public long getTimeStamp() {
    throw new UnsupportedOperationException("getTimeStamp is not implemented");
  }

  @NotNull
  public String getUrl() {
    throw new UnsupportedOperationException("getUrl is not implemented");
  }

  public boolean isDirectory() {
    throw new UnsupportedOperationException("isDirectory is not implemented");
  }

  public boolean isValid() {
    throw new UnsupportedOperationException("isValid is not implemented");
  }

  public boolean isWritable() {
    throw new UnsupportedOperationException("isWritable is not implemented");
  }

  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    throw new UnsupportedOperationException("refresh is not implemented");
  }
}