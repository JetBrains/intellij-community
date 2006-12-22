package com.intellij.localvcs;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

public class Paths {
  public static final char DELIM = '/';
  private static boolean myIsCaseSensitive = SystemInfo.isFileSystemCaseSensitive;

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

  public static boolean startsWith(String path, String prefix) {
    return myIsCaseSensitive ? path.startsWith(prefix) : StringUtil.startsWithIgnoreCase(path, prefix);
  }

  protected static void setCaseSensitive(boolean b) {
    myIsCaseSensitive = b;
  }
}
