package com.intellij.openapi.vfs;

import com.intellij.openapi.util.io.FileUtil;

public class FilePath {
  private final String myPath;

  public FilePath(String path) {
    myPath = path;
  }

  public String getPath() {
    return myPath;
  }

  @Override
  public String toString() {
    return myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return FileUtil.pathsEqual(myPath, ((FilePath)o).myPath);
  }

  @Override
  public int hashCode() {
    return FileUtil.pathHashCode(myPath);
  }
}
