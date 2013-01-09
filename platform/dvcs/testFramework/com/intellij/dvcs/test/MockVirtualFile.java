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
package com.intellij.dvcs.test;

import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * VirtualFile implementation for tests based on {@link java.io.File}.
 * Not reusing {@link com.intellij.mock.MockVirtualFile}, because the latter holds everything in memory, which is fast, but requires
 * synchronization with the real file system.
 *
 * @author Kirill Likhodedov
 */
public class MockVirtualFile extends VirtualFile {

  private static VirtualFileSystem ourFileSystem = new MockVirtualFileSystem();

  private String myPath;

  @NotNull
  static VirtualFile fromPath(@NotNull String absolutePath) {
    return new MockVirtualFile(FileUtil.toSystemIndependentName(absolutePath));
  }

  @NotNull
  static VirtualFile fromPath(@NotNull String relativePath, @NotNull Project project) {
    try {
      return fromPath(new File(project.getBaseDir().getPath() + "/" + relativePath).getCanonicalPath());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  static VirtualFile fromPath(@NotNull String relativePath, @NotNull String basePath) {
    try {
      return fromPath(new File(basePath + "/" + relativePath).getCanonicalPath());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public MockVirtualFile(@NotNull String path) {
    myPath = FileUtil.toSystemIndependentName(path);
  }

  @NotNull
  @Override
  public String getName() {
    return new File(myPath).getName();
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return ourFileSystem;
  }

  @Override
  public String getPath() {
    return myPath;
  }

  @Override
  public boolean isWritable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDirectory() {
    return new File(myPath).isDirectory();
  }

  @Override
  public boolean isValid() {
    return new File(myPath).exists();
  }

  @Override
  @Nullable
  public VirtualFile getParent() {
    File parentFile = FileUtil.getParentFile(new File(myPath));
    return parentFile != null ? new MockVirtualFile(parentFile.getPath()) : null;
  }

  @Override
  public VirtualFile[] getChildren() {
    String[] list = new File(myPath).list();
    if (list == null) {
      return EMPTY_ARRAY;
    }
    VirtualFile[] files = new VirtualFile[list.length];
    for (int i = 0; i < list.length; i++) {
      files[i] = new MockVirtualFile(myPath + "/" + list[i]);
    }
    return files;
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimeStamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLength() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  @Override
  public InputStream getInputStream() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return myPath;
  }

  @NotNull
  @Override
  public String getUrl() {
    return myPath;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return FileTypes.PLAIN_TEXT;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MockVirtualFile file = (MockVirtualFile)o;

    if (myPath != null ? !myPath.equals(file.myPath) : file.myPath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myPath != null ? myPath.hashCode() : 0;
  }

}
