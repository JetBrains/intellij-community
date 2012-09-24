package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;

import java.util.*;

/**
 * @author nik
 */
public class ArtifactBuildTargetType extends BuildTargetType {
  public static final ArtifactBuildTargetType INSTANCE = new ArtifactBuildTargetType();

  public ArtifactBuildTargetType() {
    super("artifact");
  }

  @NotNull
  @Override
  public Collection<BuildTarget<?>> computeAllTargets(@NotNull JpsModel model) {
    Collection<JpsArtifact> artifacts = JpsBuilderArtifactService.getInstance().getArtifacts(model, true);
    List<BuildTarget<?>> targets = new ArrayList<BuildTarget<?>>(artifacts.size());
    for (JpsArtifact artifact : artifacts) {
      targets.add(new ArtifactBuildTarget(artifact));
    }
    return targets;
  }

  @NotNull
  @Override
  public BuildTargetLoader createLoader(@NotNull JpsModel model) {
    return new Loader(model); 
  }

  private static class Loader extends BuildTargetLoader {
    private final Map<String, JpsArtifact> myArtifacts;

    public Loader(JpsModel model) {
      myArtifacts = new HashMap<String, JpsArtifact>();
      for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(model.getProject())) {
        myArtifacts.put(artifact.getName(), artifact);
      }
    }

    @Nullable
    @Override
    public BuildTarget createTarget(@NotNull String targetId) {
      JpsArtifact artifact = myArtifacts.get(targetId);
      return artifact != null ? new ArtifactBuildTarget(artifact) : null;
    }
  }
}
