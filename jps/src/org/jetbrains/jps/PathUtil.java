package org.jetbrains.jps;

import java.io.File;
import java.net.URI;

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
}
