// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.util;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

/**
 * @author nik
 */
public class JpsPathUtil {

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
  public static String urlToPath(@Nullable String url) {
    if (url == null) {
      return null;
    }
    if (url.startsWith("file://")) {
      return url.substring("file://".length());
    }
    else if (url.startsWith("jar://")) {
      url = url.substring("jar://".length());
      url = StringUtil.trimEnd(url, "!/");
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
    return "file://" + path;
  }

  public static String getLibraryRootUrl(File file) {
    String path = FileUtilRt.toSystemIndependentName(file.getAbsolutePath());
    return file.isDirectory() ? "file://" + path : "jar://" + path + "!/";
  }

  public static boolean isJrtUrl(@NotNull String url) {
    return url.startsWith("jrt://");
  }
}