package com.intellij.history.core;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

public class Paths {
  private static final char DELIM = '/';
  private static boolean myIsCaseSensitive;

  static {
    useSystemCaseSensitivity();
  }

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

  public static String withoutRootIfUnder(String path, String root) {
    if (!startsWith(path, root)) return null;

    path = path.substring(root.length());
    if (path.length() == 0) return "";

    if (path.charAt(0) != Paths.DELIM) return null;
    return path.substring(1);
  }

  private static boolean startsWith(String path, String prefix) {
    return myIsCaseSensitive ? path.startsWith(prefix) : StringUtil.startsWithIgnoreCase(path, prefix);
  }

  public static boolean equals(String p1, String p2) {
    return myIsCaseSensitive ? p1.equals(p2) : p1.equalsIgnoreCase(p2);
  }

  public static void setCaseSensitive(boolean b) {
    myIsCaseSensitive = b;
  }

  public static boolean isCaseSensitive() {
    return myIsCaseSensitive;
  }

  public static void useSystemCaseSensitivity() {
    myIsCaseSensitive = SystemInfo.isFileSystemCaseSensitive;
  }
}
