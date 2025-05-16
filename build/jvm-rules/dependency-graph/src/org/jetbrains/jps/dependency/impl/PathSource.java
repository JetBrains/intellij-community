// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.NodeSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class PathSource implements NodeSource {

  private final @NotNull Path myPath;

  public PathSource(@NotNull String path) {
    this(Path.of(path.replace(File.separatorChar, '/')));
  }

  public PathSource(@NotNull Path path) {
    myPath = path;
  }

  public PathSource(@NotNull GraphDataInput in) throws IOException {
    this(in.readUTF());
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    out.writeUTF(myPath.toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final PathSource that = (PathSource)o;
    return myPath.equals(that.myPath);
  }

  @Override
  public int hashCode() {
    return myPath.hashCode();
  }

  @Override
  public String toString() {
    return myPath.toString();
  }
}
