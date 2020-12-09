// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * @author yole
 */
public class CoreLocalVirtualFile extends VirtualFile {
  private final CoreLocalFileSystem myFileSystem;
  private final File myIoFile;
  private VirtualFile[] myChildren;
  private final NotNullLazyValue<Boolean> myIsDirectory;

  public CoreLocalVirtualFile(@NotNull CoreLocalFileSystem fileSystem, @NotNull File ioFile) {
    myFileSystem = fileSystem;
    myIoFile = ioFile;
    myIsDirectory = NotNullLazyValue.createValue(myIoFile::isDirectory);
  }

  public CoreLocalVirtualFile(@NotNull CoreLocalFileSystem fileSystem, @NotNull File ioFile, boolean isDirectory) {
    myFileSystem = fileSystem;
    myIoFile = ioFile;
    myIsDirectory = NotNullLazyValue.createConstantValue(isDirectory);
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
    return myIsDirectory.getValue();
  }

  @Override
  public boolean isValid() {
    return true; // Core VFS cannot change, doesn't refresh so once found, any file is writable
  }

  @Override
  public VirtualFile getParent() {
    File parentFile = myIoFile.getParentFile();
    return parentFile != null ? new CoreLocalVirtualFile(myFileSystem, parentFile, true) : null;
  }

  @Override
  public VirtualFile[] getChildren() {
    VirtualFile[] answer = myChildren;
    if (answer == null) {
      final File[] files = myIoFile.listFiles();
      if (files == null || files.length == 0) {
        answer = EMPTY_ARRAY;
      }
      else {
        answer = new VirtualFile[files.length];
        for (int i = 0; i < files.length; i++) {
          answer[i] = new CoreLocalVirtualFile(myFileSystem, files[i]);
        }
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

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
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
  public @NotNull InputStream getInputStream() throws IOException {
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
