// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PathUtil {
  private PathUtil() { }

  public static @Nullable @NlsSafe String getLocalPath(@Nullable VirtualFile file) {
    if (file == null || !file.isValid()) {
      return null;
    }
    if (file.getFileSystem().getProtocol().equals(URLUtil.JAR_PROTOCOL) && file.getParent() != null) {
      return null;
    }
    return getLocalPath(file.getPath());
  }

  public static @NotNull @NlsSafe String getLocalPath(@NotNull String path) {
    return FileUtilRt.toSystemDependentName(Strings.trimEnd(path, URLUtil.JAR_SEPARATOR));
  }

  public static @NotNull String getJarPathForClass(@NotNull Class<?> aClass) {
    String pathForClass = PathManager.getJarPathForClass(aClass);
    assert pathForClass != null : aClass;
    return pathForClass;
  }

  public static @NotNull @NlsSafe String toPresentableUrl(@NotNull String url) {
    return getLocalPath(VirtualFileManager.extractPath(url));
  }

  /**
   * @deprecated Use {@link FileUtil#toCanonicalPath(String)}
   */
  @Deprecated
  public static String getCanonicalPath(@NonNls String path) {
    return FileUtil.toCanonicalPath(path);
  }

  public static @NotNull @NlsSafe String getFileName(@NotNull String path) {
    return PathUtilRt.getFileName(path);
  }

  public static @Nullable @NlsSafe String getFileExtension(@NotNull String name) {
    return PathUtilRt.getFileExtension(name);
  }

  public static @NotNull @NlsSafe String getParentPath(@NotNull String path) {
    return PathUtilRt.getParentPath(path);
  }

  public static @NotNull @NlsSafe String suggestFileName(@NotNull String text) {
    return PathUtilRt.suggestFileName(text);
  }

  public static @NotNull @NlsSafe String suggestFileName(@NotNull String text, final boolean allowDots, final boolean allowSpaces) {
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
  public static @NlsSafe String toSystemDependentName(@Nullable String path) {
    return path == null ? null : FileUtilRt.toSystemDependentName(path);
  }

  public static @NotNull String driveLetterToLowerCase(@NotNull String path) {
    if (SystemInfoRt.isWindows && OSAgnosticPathUtil.isAbsoluteDosPath(path) && Character.isUpperCase(path.charAt(0))) {
      return Character.toLowerCase(path.charAt(0)) + path.substring(1);
    }
    return path;
  }

  public static @NotNull String makeFileName(@NotNull String name, @Nullable String extension) {
    return extension == null || extension.isEmpty() ? name : name + '.' + extension;
  }
}