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
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CoreLocalVirtualFile extends VirtualFile {
  private final CoreLocalFileSystem myFileSystem;
  private final File myIoFile;
  private VirtualFile[] myChildren;
  private final boolean isDirectory;

  public CoreLocalVirtualFile(@NotNull CoreLocalFileSystem fileSystem, @NotNull File ioFile) {
    this(fileSystem, ioFile, ioFile.isDirectory());
  }

  public CoreLocalVirtualFile(@NotNull CoreLocalFileSystem fileSystem, @NotNull File ioFile, boolean isDirectory) {
    myFileSystem = fileSystem;
    myIoFile = ioFile;
    this.isDirectory = isDirectory;
  }

  @NotNull
  @Override
  public String getName() {
    return myIoFile.getName();
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  @NotNull
  @Override
  public String getPath() {
    return FileUtil.toSystemIndependentName(myIoFile.getAbsolutePath());
  }

  @Override
  public boolean isWritable() {
    return false; // Core VFS isn't writable.
  }

  @Override
  public boolean isDirectory() {
    return isDirectory;
  }

  @Override
  public boolean isValid() {
    return true; // Core VFS cannot change, doesn't refresh so once found, any file is writable
  }

  @Override
  public VirtualFile getParent() {
    File parentFile = myIoFile.getParentFile();
    return parentFile != null ? new CoreLocalVirtualFile(myFileSystem, parentFile) : null;
  }

  @Override
  public VirtualFile[] getChildren() {
    VirtualFile[] answer = myChildren;
    if (answer == null) {
      List<VirtualFile> result = new ArrayList<VirtualFile>();
      final File[] files = myIoFile.listFiles();
      if (files == null) {
        answer = EMPTY_ARRAY;
      }
      else {
        for (File file : files) {
          result.add(new CoreLocalVirtualFile(myFileSystem, file));
        }
        answer = result.toArray(new VirtualFile[result.size()]);
      }
      myChildren = answer;
    }
    return answer;
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    return FileUtil.loadFileBytes(myIoFile);
  }

  @Override
  public long getTimeStamp() {
    return myIoFile.lastModified();
  }

  @Override
  public long getLength() {
    return myIoFile.length();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return VfsUtilCore.inputStreamSkippingBOM(new BufferedInputStream(new FileInputStream(myIoFile)), this);
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CoreLocalVirtualFile that = (CoreLocalVirtualFile)o;

    return myIoFile.equals(that.myIoFile);
  }

  @Override
  public int hashCode() {
    return myIoFile.hashCode();
  }
}
