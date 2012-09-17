package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.model.artifact.JpsArtifact;

/**
 * @author nik
 */
public class ArtifactBuildTargetType extends BuildTargetType {
  public static final ArtifactBuildTargetType INSTANCE = new ArtifactBuildTargetType();

  public ArtifactBuildTargetType() {
    super("artifact");
  }

  @Nullable
  @Override
  public BuildTarget createTarget(@NotNull String targetId, @NotNull ModuleRootsIndex rootsIndex, ArtifactRootsIndex artifactRootsIndex) {
    JpsArtifact artifact = artifactRootsIndex.getArtifact(targetId);
    return artifact != null ? new ArtifactBuildTarget(artifact) : null;
  }
}
