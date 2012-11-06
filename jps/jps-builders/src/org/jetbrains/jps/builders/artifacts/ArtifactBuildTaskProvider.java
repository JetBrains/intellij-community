package org.jetbrains.jps.builders.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuildTask;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.List;

/**
 * @author nik
 */
public abstract class ArtifactBuildTaskProvider {
  public enum ArtifactBuildPhase {
    PRE_PROCESSING, POST_PROCESSING
  }

  @NotNull
  public abstract List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact, @NotNull ArtifactBuildPhase buildPhase);
}
