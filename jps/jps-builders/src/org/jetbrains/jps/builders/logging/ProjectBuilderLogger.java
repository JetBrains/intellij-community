package org.jetbrains.jps.builders.logging;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public interface ProjectBuilderLogger {
  boolean isEnabled();

  void logDeletedFiles(Collection<String> paths);

  void logCompiledFiles(Collection<File> files, String builderName, String description) throws IOException;
}
