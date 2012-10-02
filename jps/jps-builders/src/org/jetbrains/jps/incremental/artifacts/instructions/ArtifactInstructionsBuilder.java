package org.jetbrains.jps.incremental.artifacts.instructions;

import java.util.List;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilder {

  List<ArtifactRootDescriptor> getDescriptors();
}
