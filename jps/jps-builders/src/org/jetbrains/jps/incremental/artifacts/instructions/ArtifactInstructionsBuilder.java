package org.jetbrains.jps.incremental.artifacts.instructions;

import java.io.IOException;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilder {
  void processRoots(ArtifactRootProcessor processor) throws IOException;
}
