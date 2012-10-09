package org.jetbrains.jps.incremental;

import java.util.Collection;

/**
 * @author nik
 */
public interface ProjectBuilderLogger {
  boolean isEnabled();
  void logDeletedFiles(Collection<String> paths);
}
