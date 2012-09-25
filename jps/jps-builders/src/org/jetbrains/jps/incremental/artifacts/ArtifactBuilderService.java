package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ProjectLevelBuilder;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactBuilderService extends BuilderService {
  @Override
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    return Collections.singletonList(ArtifactBuildTargetType.INSTANCE);
  }

  @NotNull
  @Override
  public List<? extends ProjectLevelBuilder> createProjectLevelBuilders() {
    return Collections.singletonList(new IncArtifactBuilder());
  }
}
