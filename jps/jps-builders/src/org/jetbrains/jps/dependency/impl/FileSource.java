// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.NodeSource;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class FileSource implements NodeSource {

  private final Path myPath;

  public FileSource(@NotNull File file) {
    this(file.toPath());
  }
  
  public FileSource(@NotNull Path path) {
    myPath = path;
  }

  public FileSource(@NotNull DataInput in) throws IOException {
    myPath = Path.of(RW.readUTF(in));
  }

  @Override
  public void write(DataOutput out) throws IOException {
    RW.writeUTF(out, myPath.toString());
  }

  @Override
  public Path getPath() {
    return myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final FileSource that = (FileSource)o;

    if (!myPath.equals(that.myPath)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myPath.hashCode();
  }

  @Override
  public String toString() {
    return "NodeSource {" + myPath + "}";
  }
}
