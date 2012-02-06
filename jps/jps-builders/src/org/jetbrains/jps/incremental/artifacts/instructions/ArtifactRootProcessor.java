package org.jetbrains.jps.incremental.artifacts.instructions;

import java.util.Collection;

/**
 * @author nik
 */
public interface ArtifactRootProcessor {
  void process(ArtifactSourceRoot root, Collection<DestinationInfo> destinations) throws Exception;
}
