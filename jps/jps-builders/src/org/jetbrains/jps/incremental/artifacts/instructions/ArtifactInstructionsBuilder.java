package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilder {
  void processRoots(ArtifactRootProcessor processor) throws Exception;

  void processContainingRoots(String filePath, ArtifactRootProcessor processor) throws Exception;

  @Nullable
  JarInfo getJarInfo(String outputPath);
}
