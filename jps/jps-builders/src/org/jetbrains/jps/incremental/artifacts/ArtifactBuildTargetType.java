package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;

import java.util.*;

/**
 * @author nik
 */
public class ArtifactBuildTargetType extends BuildTargetType<ArtifactBuildTarget> {
  public static final ArtifactBuildTargetType INSTANCE = new ArtifactBuildTargetType();

  public ArtifactBuildTargetType() {
    super("artifact");
  }

  @NotNull
  @Override
  public Collection<ArtifactBuildTarget> computeAllTargets(@NotNull JpsModel model) {
    Collection<JpsArtifact> artifacts = JpsBuilderArtifactService.getInstance().getArtifacts(model, true);
    List<ArtifactBuildTarget> targets = new ArrayList<ArtifactBuildTarget>(artifacts.size());
    for (JpsArtifact artifact : artifacts) {
      targets.add(new ArtifactBuildTarget(artifact));
    }
    return targets;
  }

  @NotNull
  @Override
  public Loader createLoader(@NotNull JpsModel model) {
    return new Loader(model); 
  }

  private static class Loader extends BuildTargetLoader<ArtifactBuildTarget> {
    private final Map<String, JpsArtifact> myArtifacts;

    public Loader(JpsModel model) {
      myArtifacts = new HashMap<String, JpsArtifact>();
      for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(model.getProject())) {
        myArtifacts.put(artifact.getName(), artifact);
      }
    }

    @Nullable
    @Override
    public ArtifactBuildTarget createTarget(@NotNull String targetId) {
      JpsArtifact artifact = myArtifacts.get(targetId);
      return artifact != null ? new ArtifactBuildTarget(artifact) : null;
    }
  }
}
