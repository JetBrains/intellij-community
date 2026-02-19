
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

class DummyDirectoryImpl extends DummyFileBase {
  private final List<DummyFileBase> myChildren = new ArrayList<>();

  DummyDirectoryImpl(@NotNull DummyFileSystem fileSystem, DummyDirectoryImpl parent, @NotNull String name) {
    super(fileSystem, parent, name);
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public long getLength() {
    return 0;
  }

  @Override
  public VirtualFile[] getChildren() {
    return myChildren.isEmpty() ? EMPTY_ARRAY : myChildren.toArray(VirtualFile.EMPTY_ARRAY);
  }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    throw new IOException(AnalysisBundle.message("file.read.error", getUrl()));
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new IOException(AnalysisBundle.message("file.write.error", getUrl()));
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    throw new IOException(AnalysisBundle.message("file.read.error", getUrl()));
  }

  @Override
  public long getModificationStamp() {
    return -1;
  }

  void addChild(DummyFileBase child) {
    myChildren.add(child);
  }

  void removeChild(DummyFileBase child) {
    myChildren.remove(child);
    child.myIsValid = false;
  }
}
