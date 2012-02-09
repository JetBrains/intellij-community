package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ProjectLevelBuilder;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactBuilderService extends BuilderService {
  @NotNull
  @Override
  public List<? extends ProjectLevelBuilder> createProjectLevelBuilders() {
    return Collections.singletonList(new IncArtifactBuilder());
  }
}
