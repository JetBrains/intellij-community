package org.jetbrains.jps.incremental.java;

/**
 * @author nik
 */
public interface JavaBuilderLogger {
  void log(String line);
  boolean isEnabled();
}
