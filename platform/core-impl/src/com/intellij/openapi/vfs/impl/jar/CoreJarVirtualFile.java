// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author yole
 */
final class CoreJarVirtualFile extends VirtualFile {
  private final CoreJarHandler myHandler;
  private final CharSequence myName;
  private final long myLength;
  private final long myTimestamp;
  private final VirtualFile myParent;
  private VirtualFile[] myChildren = VirtualFile.EMPTY_ARRAY;

  CoreJarVirtualFile(@NotNull CoreJarHandler handler,
                     @NotNull CharSequence name,
                     long length,
                     long timestamp,
                     @Nullable CoreJarVirtualFile parent) {
    myHandler = handler;
    myName = name;
    myLength = length;
    myTimestamp = timestamp;
    myParent = parent;
  }

  void setChildren(VirtualFile[] children) {
    myChildren = children;
  }

  @NotNull
  @Override
  public String getName() {
    return myName.toString();
  }

  @NotNull
  @Override
  public CharSequence getNameSequence() {
    return myName;
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return myHandler.getFileSystem();
  }

  @Override
  @NotNull
  public String getPath() {
    if (myParent == null) {
      return FileUtil.toSystemIndependentName(myHandler.getFile().getPath()) + "!/";
    }

    String parentPath = myParent.getPath();
    StringBuilder answer = new StringBuilder(parentPath.length() + 1 + myName.length());
    answer.append(parentPath);
    if (answer.charAt(answer.length() - 1) != '/') {
      answer.append('/');
    }
    answer.append(myName);

    return answer.toString();
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isDirectory() {
    return myLength < 0;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public VirtualFile getParent() {
    return myParent;
  }

  @Override
  public VirtualFile[] getChildren() {
    return myChildren;
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException("JarFileSystem is read-only");
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    Couple<String> pair = CoreJarFileSystem.splitPath(getPath());
    return myHandler.contentsToByteArray(pair.second);
  }

  @Override
  public long getTimeStamp() {
    return myTimestamp;
  }

  @Override
  public long getLength() {
    return myLength;
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) { }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    return new BufferExposingByteArrayInputStream(contentsToByteArray());
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }
}
