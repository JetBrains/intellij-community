/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.dvcs.test

import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
/**
 * VirtualFile implementation for tests based on {@link java.io.File}.
 * Not reusing {@link com.intellij.mock.MockVirtualFile}, because the latter holds everything in memory, which is fast, but requires
 * synchronization with the real file system.
 *
 * @author Kirill Likhodedov
 */
class MockVirtualFile extends VirtualFile {

  private static VirtualFileSystem ourFileSystem = new MockVirtualFileSystem()

  private String myPath;

  @NotNull
  static VirtualFile fromPath(@NotNull String absolutePath) {
    return new MockVirtualFile(FileUtil.toSystemIndependentName(absolutePath))
  }

  @NotNull
  static VirtualFile fromPath(@NotNull String relativePath, @NotNull Project project) {
    return fromPath(new File(project.getBaseDir().getPath() + "/" + relativePath).getCanonicalPath())
  }

  @NotNull
  static VirtualFile fromPath(@NotNull String relativePath, @NotNull String basePath) {
    return fromPath(new File(basePath + "/" + relativePath).getCanonicalPath())
  }

  MockVirtualFile(@NotNull String path) {
    myPath = FileUtil.toSystemIndependentName(path);
  }

  @NotNull
  @Override
  String getName() {
    new File(path).getName();
  }

  @NotNull
  @Override
  VirtualFileSystem getFileSystem() {
    ourFileSystem
  }

  @Override
  String getPath() {
    myPath;
  }

  @Override
  boolean isWritable() {
    throw new UnsupportedOperationException();
  }

  @Override
  boolean isDirectory() {
    new File(path).directory;
  }

  @Override
  boolean isValid() {
    new File(myPath).exists()
  }

  @Override
  @Nullable
  VirtualFile getParent() {
    File parentFile = FileUtil.getParentFile(new File(path))
    parentFile ? new MockVirtualFile(parentFile.path) : null;
  }

  @Override
  VirtualFile[] getChildren() {
    new File(path).list().collect { new MockVirtualFile("$path/$it") }
  }

  @NotNull
  @Override
  OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  byte[] contentsToByteArray() {
    throw new UnsupportedOperationException();
  }

  @Override
  long getTimeStamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  long getLength() {
    throw new UnsupportedOperationException();
  }

  @Override
  void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  @Override
  InputStream getInputStream() {
    throw new UnsupportedOperationException();
  }

  @Override
  String toString() {
    return myPath;
  }

  @NotNull
  @Override
  String getUrl() {
    myPath;
  }

  @Override
  FileType getFileType() {
    return FileTypes.PLAIN_TEXT;
  }

  boolean equals(o) {
    if (this.is(o)) return true
    if (getClass() != o.class) return false
    MockVirtualFile file = (MockVirtualFile)o
    if (myPath != file.myPath) return false
    return true
  }

  int hashCode() {
    return (myPath != null ? myPath.hashCode() : 0)
  }

}
