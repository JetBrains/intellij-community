package org.jetbrains.jps;

import java.io.File;
import java.net.URI;
import java.util.Set;

/**
 * @author nik
 */
public class PathUtil {
  public static String toSystemIndependentPath(String path) {
    return path.replace('\\', '/');
  }

  public static String toPath(URI uri) {
    if (uri.getScheme() == null) {
      return uri.getPath();
    }
    return new File(uri).getAbsolutePath();
  }

  public static boolean isUnder(Set<File> ancestors, File file) {
    File current = file;
    while (current != null) {
      if (ancestors.contains(current)) {
        return true;
      }
      current = current.getParentFile();
    }
    return false;
  }
}
