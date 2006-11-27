package com.intellij.localvcs;

import java.io.File;

public class Path {
  private static final String DELIM = "/";

  public static String getNameOf(String path) {
    int i = path.lastIndexOf(DELIM);
    return i == -1 ? path : path.substring(i + 1);
  }

  public static String getParentOf(String path) {
    int i = path.lastIndexOf(DELIM);
    return i == -1 ? null : path.substring(0, i);
  }

  public static String appended(String path, String child) {
    return path + DELIM + child;
  }

  public static String renamed(String path, String newName) {
    String parent = getParentOf(path);
    return parent == null ? newName : appended(parent, newName);
  }
}
