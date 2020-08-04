// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;

public final class PathUtil {
  private PathUtil() { }

  @Nullable
  public static String getLocalPath(@Nullable VirtualFile file) {
    if (file == null || !file.isValid()) {
      return null;
    }
    if (file.getFileSystem().getProtocol().equals(URLUtil.JAR_PROTOCOL) && file.getParent() != null) {
      return null;
    }
    return getLocalPath(file.getPath());
  }

  @NotNull
  public static String getLocalPath(@NotNull String path) {
    return FileUtil.toSystemDependentName(StringUtil.trimEnd(path, URLUtil.JAR_SEPARATOR));
  }

  @NotNull
  public static String getJarPathForClass(@NotNull Class<?> aClass) {
    final String pathForClass = PathManager.getJarPathForClass(aClass);
    assert pathForClass != null : aClass;
    return pathForClass;
  }

  @NotNull
  public static String toPresentableUrl(@NotNull String url) {
    return getLocalPath(VirtualFileManager.extractPath(url));
  }

  /**
   * @deprecated Use {@link FileUtil#toCanonicalPath(String)}
   */
  @Deprecated
  public static String getCanonicalPath(@NonNls String path) {
    return FileUtil.toCanonicalPath(path);
  }

  @NotNull
  public static String getFileName(@NotNull String path) {
    return PathUtilRt.getFileName(path);
  }

  @Nullable
  public static String getFileExtension(@NotNull String name) {
    return PathUtilRt.getFileExtension(name);
  }

  @NotNull
  public static String getParentPath(@NotNull String path) {
    return PathUtilRt.getParentPath(path);
  }

  @NotNull
  public static String suggestFileName(@NotNull String text) {
    return PathUtilRt.suggestFileName(text);
  }

  @NotNull
  public static String suggestFileName(@NotNull String text, final boolean allowDots, final boolean allowSpaces) {
    return PathUtilRt.suggestFileName(text, allowDots, allowSpaces);
  }

  public static boolean isValidFileName(@NotNull String fileName) {
    return PathUtilRt.isValidFileName(fileName, true);
  }

  public static boolean isValidFileName(@NotNull String fileName, boolean strict) {
    return PathUtilRt.isValidFileName(fileName, strict);
  }

  @Contract("null -> null; !null -> !null")
  public static String toSystemIndependentName(@Nullable String path) {
    return path == null ? null : FileUtilRt.toSystemIndependentName(path);
  }

  @Contract("null -> null; !null -> !null")
  public static String toSystemDependentName(@Nullable String path) {
    return path == null ? null : FileUtilRt.toSystemDependentName(path);
  }

  @NotNull
  public static String driveLetterToLowerCase(@NotNull String path) {
    if (SystemInfo.isWindows && FileUtil.isWindowsAbsolutePath(path) && Character.isUpperCase(path.charAt(0))) {
      return Character.toLowerCase(path.charAt(0)) + path.substring(1);
    }
    return path;
  }

  @NotNull
  public static String makeFileName(@NotNull String name, @Nullable String extension) {
    return StringUtil.isEmpty(extension) ? name : name + '.' + extension;
  }

  /**
   * @return true if the {@code file} path is equal to the {@code path},
   * according to the file's parent directories case sensitivity.
   */
  public static boolean pathEqualsTo(@NotNull VirtualFile file, @NotNull @SystemIndependent String path) {
    path = FileUtil.toCanonicalPath(path);
    int li = path.length();
    while (file != null && li != -1) {
      int i = path.lastIndexOf('/', li-1);
      CharSequence fileName = file.getNameSequence();
      if (StringUtil.endsWithChar(fileName, '/')) {
        fileName = fileName.subSequence(0, fileName.length()-1);
      }
      if (!StringUtil.equal(fileName, path.substring(i + 1, li), file.isCaseSensitive())) {
        return false;
      }
      file = file.getParent();
      li = i;
    }
    return li == -1 && file == null;
  }

  private static @NotNull List<VirtualFile> getHierarchy(@NotNull VirtualFile file) {
    List<VirtualFile> result = new ArrayList<>();
    while (file != null) {
      result.add(file);
      file = file.getParent();
    }
    return result;
  }

  public static boolean isAncestorOrSelf(@NotNull @SystemIndependent String ancestorPath, @NotNull VirtualFile file) {
    ancestorPath = FileUtil.toCanonicalPath(ancestorPath);
    List<VirtualFile> hierarchy = getHierarchy(file);
    if (ancestorPath.isEmpty()) {
      return true;
    }
    int i = 0;
    boolean result = false;
    int j;
    for (j = hierarchy.size() - 1; j >= 0; j--) {
      VirtualFile part = hierarchy.get(j);
      String name = part.getName();
      boolean matches = part.isCaseSensitive() ? StringUtil.startsWith(ancestorPath, i, name) :
                        StringUtil.startsWithIgnoreCase(ancestorPath, i, name);
      if (!matches) {
        break;
      }
      i += name.length();
      if (!name.endsWith("/")) {
        if (i != ancestorPath.length() && ancestorPath.charAt(i) != '/') {
          break;
        }
        i++;
      }
      if (i >= ancestorPath.length()) {
        result = true;
        break;
      }
    }
    return result;
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link com.intellij.openapi.vfs.VfsUtil#getLocalFile(VirtualFile)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @NotNull
  public static VirtualFile getLocalFile(@NotNull VirtualFile file) {
    if (file.isValid()) {
      VirtualFileSystem fileSystem = file.getFileSystem();
      if (fileSystem instanceof LocalFileProvider) {
        VirtualFile localFile = ((LocalFileProvider)fileSystem).getLocalVirtualFileFor(file);
        if (localFile != null) {
          return localFile;
        }
      }
    }

    return file;
  }
  //</editor-fold>
}