package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilder {
  void processRoots(ArtifactRootProcessor processor) throws IOException;

  void processContainingRoots(String filePath, ArtifactRootProcessor processor) throws IOException;

  @Nullable
  JarInfo getJarInfo(String outputPath);
}
