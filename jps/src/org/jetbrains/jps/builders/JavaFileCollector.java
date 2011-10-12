package org.jetbrains.jps.builders;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class JavaFileCollector {
  public static void collectRecursively(File file, List<File> result, Set<File> excluded) {
    if (file.isDirectory()) {
      File current = file;
      while (current != null) {
        if (excluded.contains(current)) return;
        current = current.getParentFile();
      }

      final File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          collectRecursively(child, result, excluded);
        }
      }
      return;
    }

    if (file.getName().endsWith(".java")) {
      result.add(file);
    }
  }
}
