// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.NodeSource;

import java.io.File;
import java.io.IOException;

public final class PathSource implements NodeSource {

  private final @NotNull String myPath;

  public PathSource(@NotNull String path) {
    myPath = File.separatorChar != '/'? path.replace(File.separatorChar, '/') : path;
  }

  public PathSource(@NotNull GraphDataInput in) throws IOException {
    myPath = in.readUTF();
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    out.writeUTF(myPath);
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

    if (Strings.areSameInstance(myPath, that.myPath)) {
      return true;
    }
    return SystemInfoRt.isFileSystemCaseSensitive? myPath.equals(that.myPath) : myPath.equalsIgnoreCase(that.myPath);
  }

  @Override
  public int hashCode() {
    if (myPath.isEmpty()) {
      return 0;
    }
    return SystemInfoRt.isFileSystemCaseSensitive? myPath.hashCode() : StringUtilRt.stringHashCodeInsensitive(myPath);
  }

  @Override
  public String toString() {
    return myPath;
  }
}
