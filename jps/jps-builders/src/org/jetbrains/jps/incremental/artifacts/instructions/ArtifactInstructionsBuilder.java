package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilder {
  void processRoots(ArtifactRootProcessor processor) throws IOException;

  @Nullable
  JarInfo getJarInfo(String outputPath);

  int getRootIndex(@NotNull ArtifactSourceRoot root);
}
