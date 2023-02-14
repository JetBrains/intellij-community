// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.util;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
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

  @NotNull
  public static String urlToOsPath(@NotNull String url) {
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
      url = url.substring(JAR_URL_PREFIX.length());
      url = Strings.trimEnd(url, JAR_SEPARATOR);
    }
    return url;
  }

  //todo[nik] copied from VfsUtil
  @NotNull
  public static String fixURLforIDEA(@NotNull String url) {
    int idx = url.indexOf(":/");
    if (idx >= 0 && idx + 2 < url.length() && url.charAt(idx + 2) != '/') {
      String prefix = url.substring(0, idx);
      String suffix = url.substring(idx + 2);

      if (SystemInfoRt.isWindows) {
        url = prefix + "://" + suffix;
      }
      else {
        url = prefix + ":///" + suffix;
      }
    }
    return url;
  }

  public static String pathToUrl(String path) {
    return FILE_URL_PREFIX + path;
  }

  public static String getLibraryRootUrl(File file) {
    String path = FileUtilRt.toSystemIndependentName(file.getAbsolutePath());
    return file.isDirectory() ? FILE_URL_PREFIX + path : JAR_URL_PREFIX
                                                         + path + "!/";
  }

  public static boolean isJrtUrl(@NotNull String url) {
    return url.startsWith("jrt://");
  }

  public static @Nullable String readProjectName(@NotNull Path projectDir) {
    try (Stream<String> stream = Files.lines(projectDir.resolve(".name"))) {
      return stream.findFirst().map(String::trim).orElse(null);
    }
    catch (IOException | UncheckedIOException e) {
      return null;
    }
  }

  private static final String UNNAMED_PROJECT = "<unnamed>";

  public static @NotNull String getDefaultProjectName(@NotNull Path projectDir) {
    Path parent = projectDir.getParent();
    if (parent != null) {
      Path name = parent.getFileName();  // `null` when parent is a Windows disk root
      return name != null ? name.toString() : parent.toString();
    }
    else {
      return UNNAMED_PROJECT;
    }
  }
}