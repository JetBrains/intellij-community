package org.jetbrains.jps.util;

import com.intellij.openapi.util.io.FileUtilRt;

import java.io.File;
import java.util.Set;

/**
 * @author nik
 */
public class JpsPathUtil {

  public static boolean isUnder(Set<File> ancestors, File file) {
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
    return new File(FileUtilRt.toSystemDependentName(urlToPath(url)));
  }

  public static String urlToPath(String url) {
    if (url == null) return null;
    if (url.startsWith("file://")) {
      return url.substring("file://".length());
    }
    else if (url.startsWith("jar://")) {
      url = url.substring("jar://".length());
      if (url.endsWith("!/")) {
        url = url.substring(0, url.length() - "!/".length());
      }
    }
    return url;
  }

  public static String pathToUrl(String path) {
    return "file://" + path;
  }

  public static String getLibraryRootUrl(File file) {
    String path = FileUtilRt.toSystemIndependentName(file.getAbsolutePath());
    if (file.isDirectory()) {
      return "file://" + path;
    }
    return "jar://" + path + "!/";
  }
}
