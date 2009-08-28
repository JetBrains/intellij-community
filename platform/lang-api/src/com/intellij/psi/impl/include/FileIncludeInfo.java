package com.intellij.psi.impl.include;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class FileIncludeInfo {

  public final String fileName;
  public final String path;
  public final int offset;
  public final boolean runtimeOnly;

  public FileIncludeInfo(@NotNull String fileName, @NotNull String path, int offset, boolean runtimeOnly) {
    this.fileName = fileName;
    this.path = path;
    this.offset = offset;
    this.runtimeOnly = runtimeOnly;
  }

  public FileIncludeInfo(@NotNull String fileName, @NotNull String path, int offset) {
    this(fileName, path, offset, false);
  }

  public FileIncludeInfo(@NotNull String fileName, @NotNull String path) {
    this(fileName, path, -1, false);
  }

  public FileIncludeInfo(@NotNull String path) {
    this(getFileName(path), path, -1, false);
  }

  private static String getFileName(String path) {
    int pos = path.lastIndexOf('/');
    return pos == -1 ? path : path.substring(pos + 1);
  }

}
