package org.jetbrains.jps.idea;

/**
 * @author nik
 */
public interface ProjectLoadingErrorReporter {
  void error(String message);

  void warning(String message);

  void info(String message);
}
