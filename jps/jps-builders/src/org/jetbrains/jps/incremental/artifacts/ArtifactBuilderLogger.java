package org.jetbrains.jps.incremental.artifacts;

/**
 * @author nik
 */
public interface ArtifactBuilderLogger {
  void fileCopied(String sourceFilePath);

  void fileDeleted(String targetFilePath);

  boolean isEnabled();
}
