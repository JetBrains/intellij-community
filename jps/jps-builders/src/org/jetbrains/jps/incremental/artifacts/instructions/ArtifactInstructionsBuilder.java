package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.Pair;

import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilder {
  void processRoots(ArtifactRootProcessor processor) throws IOException;

  List<Pair<ArtifactRootDescriptor, DestinationInfo>> getInstructions();
}
