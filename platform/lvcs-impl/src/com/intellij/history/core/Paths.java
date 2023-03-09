// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.UrlFilePath;
import com.intellij.openapi.vfs.VersionedFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class Paths {
  public static final char DELIM = '/';
  private static boolean myIsCaseSensitive;

  static {
    useSystemCaseSensitivity();
  }

  public static String getNameOf(String path) {
    int i = path.lastIndexOf(DELIM);
    if (i == -1 || path.length() == 1) return path;
    return path.substring(i + 1);
  }

  public static String getParentOf(String path) {
    int i = path.lastIndexOf(DELIM);
    if (i == -1) return "";
    if (i == 0) i = 1;
    return path.substring(0, i);
  }

  public static String appended(String path, String child) {
    path = appendParent(path);
    return path + child;
  }

  public static String renamed(String path, String newName) {
    return appended(getParentOf(path), newName);
  }

  public static String reparented(String path, String newParentPath) {
    return appended(newParentPath, getNameOf(path));
  }

  public static String relativeIfUnder(String path, String root) {
    if (!isParent(root, path)) return null;

    path = path.substring(root.length());
    if (path.length() == 0) return "";

    if (path.charAt(0) != DELIM) return null;
    return path.substring(1);
  }

  public static Iterable<String> split(String path) {
    String root = FileUtil.extractRootPath(path);
    if (root == null) return splitInner(path);
    if (root.length() + 1 == path.length() && path.endsWith("/")) {
      return Collections.singleton(root);
    }

    List<String> tail = splitInner(path.substring(root.length()));
    return ContainerUtil.concat(Collections.singletonList(root), tail);
  }

  @NotNull
  private static List<String> splitInner(String path) {
    if (path.isEmpty()) return Collections.emptyList();
    int s = 0;
    int e = path.length();
    if (path.charAt(0) == '/') ++s;
    if (e > s && path.charAt(e - 1) == '/') --e;
    return StringUtil.split(path.substring(s, e), String.valueOf(DELIM), true, false);
  }

  public static boolean isParent(String parent, String path) {
    if (equals(parent, path)) return true;
    parent = appendParent(parent);
    return myIsCaseSensitive ? path.startsWith(parent) : StringUtil.startsWithIgnoreCase(path, parent);
  }

  private static String appendParent(String parent) {
    if (parent.isEmpty()) return parent;
    if (parent.charAt(parent.length() - 1) != DELIM) parent += DELIM;
    return parent;
  }

  public static boolean isParentOrChild(String p1, String p2) {
    return isParent(p1, p2) || isParent(p2, p1);
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

  @NotNull
  public static FilePath createDvcsFilePath(@NotNull String path, boolean isDirectory) {
    return path.contains(URLUtil.SCHEME_SEPARATOR)
           ? new UrlFilePath(path, isDirectory)
           : new LocalFilePath(path, isDirectory);
  }

  @NotNull
  public static FilePath createDvcsFilePath(@NotNull VirtualFile file) {
    if (file.getFileSystem() instanceof VersionedFileSystem) {
      return new UrlFilePath(file.getUrl(), file.isDirectory());
    }
    else {
      return new LocalFilePath(file.getPath(), file.isDirectory());
    }
  }
}
