package org.jetbrains.jps.incremental.artifacts.instructions;

import java.io.IOException;

/**
 * @author nik
 */
public interface ArtifactRootProcessor {
  boolean process(ArtifactRootDescriptor descriptor, DestinationInfo destinations) throws IOException;
}
