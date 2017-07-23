/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class PathUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.PathUtil");

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
  public static VirtualFile getLocalFile(@NotNull VirtualFile file) {
    if (!file.isValid()) {
      return file;
    }
    if (file.getFileSystem() instanceof LocalFileProvider) {
      final VirtualFile localFile = ((LocalFileProvider)file.getFileSystem()).getLocalVirtualFileFor(file);
      if (localFile != null) {
        return localFile;
      }
    }
    return file;
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
    int index = name.lastIndexOf('.');
    if (index < 0) return null;
    return name.substring(index + 1);
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

  /**
   * Ensures that the given argument doesn't contain {@code \} separators.
   * <p>
   * The violations are reported via the {@code LOG.error}.
   * <p>
   * TODO SystemIndependentInstrumentingBuilder now embeds assertions directly, so we can remove this method.
   *
   * @param className     Class name
   * @param methodName    Method name
   * @param parameterName Parameter name
   * @param argument      Path
   * @see SystemDependent
   * @see SystemIndependent
   */
  @Deprecated
  public static void assertArgumentIsSystemIndependent(String className, String methodName, String parameterName, String argument) {
    if (argument != null && argument.contains("\\")) {
      String message = String.format("Argument for @SystemIndependent parameter '%s' of %s.%s must be system-independent: %s",
                                     parameterName, className, methodName, argument);

      IllegalArgumentException exception = new IllegalArgumentException(message);

      StackTraceElement[] stackTrace = new StackTraceElement[exception.getStackTrace().length - 1];
      System.arraycopy(exception.getStackTrace(), 1, stackTrace, 0, stackTrace.length);
      exception.setStackTrace(stackTrace);

      LOG.error(exception);
    }
  }

  @NotNull
  public static String driveLetterToLowerCase(@NotNull String path) {
    if (SystemInfo.isWindows && path.length() >= 2 && Character.isUpperCase(path.charAt(0)) && path.charAt(1) == ':') {
      File file = new File(path);
      if (file.isAbsolute()) {
        return Character.toLowerCase(path.charAt(0)) + path.substring(1);
      }
    }
    return path;
  }

  @NotNull
  public static String makeFileName(@NotNull String name, @Nullable String extension) {
    return StringUtil.isEmpty(extension) ? name : name + '.' + extension;
  }
}