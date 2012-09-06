package org.jetbrains.jps.model.java.compiler;

import java.io.File;

/**
 * @author nik
 */
public interface JpsCompilerExcludes {
  void addExcludedFile(String url);

  void addExcludedDirectory(String url, boolean recursively);

  boolean isExcluded(File file);
}
