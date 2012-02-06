package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.ProjectLevelBuilder;
import org.jetbrains.jps.incremental.ProjectLevelBuilderService;

/**
 * @author nik
 */
public class ArtifactBuilderService extends ProjectLevelBuilderService {
  @NotNull
  @Override
  public ProjectLevelBuilder createBuilder() {
    return new IncArtifactBuilder();
  }
}
