// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.util.SystemInfo;

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
    return SystemInfo.isFileSystemCaseSensitive? myPath.equals(that.myPath) : myPath.equalsIgnoreCase(that.myPath);
  }

  @Override
  public int hashCode() {
    if (myPath.isEmpty()) {
      return 0;
    }
    return SystemInfo.isFileSystemCaseSensitive? myPath.hashCode() : stringHashCodeInsensitive(myPath);
  }

  @Override
  public String toString() {
    return myPath;
  }

  private static int stringHashCodeInsensitive(@NotNull CharSequence chars) {
    int h = 0;
    for (int off = 0; off < chars.length(); off++) {
      h = 31 * h + toLowerCase(chars.charAt(off));
    }
    return h;
  }

  private static char toLowerCase(char a) {
    if (a <= 'z') {
      return a >= 'A' && a <= 'Z' ? (char)(a + ('a' - 'A')) : a;
    }
    return Character.toLowerCase(a);
  }
}
