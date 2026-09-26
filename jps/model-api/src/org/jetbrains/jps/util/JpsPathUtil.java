// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.util;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

public final class JpsPathUtil {

  public static final String FILE_URL_PREFIX = "file://";
  public static final String JAR_URL_PREFIX = "jar://";
  public static final String JAR_SEPARATOR = "!/";

  public static boolean isUnder(Set<File> ancestors, File file) {
    if (ancestors.isEmpty()) {
      return false; // optimization
    }
    File current = file;
    while (current != null) {
      if (ancestors.contains(current)) {
        return true;
      }
      current = FileUtilRt.getParentFile(current);
    }
    return false;
  }

  public static File urlToFile(String url) {
    return new File(urlToOsPath(url));
  }

  public static @NotNull Path urlToNioPath(@NotNull String url) {
    return Path.of(urlToOsPath(url));
  }

  public static @NotNull String urlToOsPath(@NotNull String url) {
    return FileUtilRt.toSystemDependentName(urlToPath(url));
  }

  @Contract("null -> null; !null -> !null")
  public static @NlsSafe String urlToPath(@Nullable String url) {
    if (url == null) {
      return null;
    }
    if (url.startsWith(FILE_URL_PREFIX)) {
      return url.substring(FILE_URL_PREFIX.length());
    }
    else if (url.startsWith(JAR_URL_PREFIX)) {
      int p = url.lastIndexOf(JAR_SEPARATOR);
      url = p >= 0 ? url.substring(JAR_URL_PREFIX.length(), p) : url.substring(JAR_URL_PREFIX.length());
    }
    return url;
  }

  public static String pathToUrl(String path) {
    return FILE_URL_PREFIX + path;
  }

  public static @NotNull String getLibraryRootUrl(@NotNull Path path) {
    Path absolutePath = path.toAbsolutePath();
    String fileName = absolutePath.getFileName().toString();
    String pathString = FileUtilRt.toSystemIndependentName(absolutePath.toString());
    return fileName.endsWith(".jar") || fileName.endsWith(".zip") ? JAR_URL_PREFIX + pathString + "!/" : FILE_URL_PREFIX + pathString;
  }
  
  public static @NotNull String getLibraryRootUrl(File file) {
    String path = FileUtilRt.toSystemIndependentName(file.getAbsolutePath());
    return file.isDirectory() ? FILE_URL_PREFIX + path : JAR_URL_PREFIX + path + "!/";
  }

  public static boolean isJrtUrl(@NotNull String url) {
    return url.startsWith("jrt://");
  }

  @ApiStatus.Internal
  public static @Nullable String readProjectName(@NotNull Path projectDir) {
    String s;
    try (Stream<String> stream = Files.lines(projectDir.resolve(".name"))) {
      s = stream.findFirst().orElse("");
    }
    catch (IOException | UncheckedIOException e) {
      return null;
    }
    return normalizeProjectName(s);
  }

  @ApiStatus.Internal
  public static @Nullable String normalizeProjectName(@Nullable String s) {
    if (s == null) {
      return null;
    }

    s = StringUtil.removeHtmlTags(s, true);
    return s.isBlank() ? null : s.trim();
  }

  private static final String UNNAMED_PROJECT = "<unnamed>";
}
