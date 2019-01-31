// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PathUtil {

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
  public static String getJarPathForClass(@NotNull Class aClass) {
    final String pathForClass = PathManager.getJarPathForClass(aClass);
    assert pathForClass != null : aClass;
    return pathForClass;
  }

  @NotNull
  public static String toPresentableUrl(@NotNull String url) {
    return getLocalPath(VirtualFileManager.extractPath(url));
  }

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
    if (SystemInfo.isWindows && FileUtil.isWindowsAbsolutePath(path)) {
      return Character.toLowerCase(path.charAt(0)) + path.substring(1);
    }
    return path;
  }

  @NotNull
  public static String makeFileName(@NotNull String name, @Nullable String extension) {
    return StringUtil.isEmpty(extension) ? name : name + '.' + extension;
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link com.intellij.openapi.vfs.VfsUtil#getLocalFile(VirtualFile)} instead (to be removed in IDEA 2019) */
  @Deprecated
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