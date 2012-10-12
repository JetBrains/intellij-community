package org.jetbrains.jps.builders.logging;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * @author nik
 */
public interface ProjectBuilderLogger {
  boolean isEnabled();

  void logDeletedFiles(Collection<String> paths);

  void logCompiledFiles(Set<File> files, String builderName, String description) throws IOException;
}
