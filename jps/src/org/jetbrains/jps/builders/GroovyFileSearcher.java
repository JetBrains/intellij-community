package org.jetbrains.jps.builders;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public class GroovyFileSearcher {
  public static boolean containGroovyFiles(List<? extends CharSequence> paths) {
    for (CharSequence path : paths) {
      if (containGroovyFiles(new File(path.toString()))) {
        return true;
      }
    }
    return false;
  }

  private static boolean containGroovyFiles(File file) {
    final File[] files = file.listFiles();
    if (files != null) {
      for (File child : files) {
        if (containGroovyFiles(child)) {
          return true;
        }
      }
      return false;
    }
    return file.getName().endsWith(".groovy");
  }
}
