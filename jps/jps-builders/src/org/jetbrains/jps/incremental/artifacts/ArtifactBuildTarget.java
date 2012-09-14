package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public class ArtifactBuildTarget extends BuildTarget {
  private final JpsArtifact myArtifact;

  public ArtifactBuildTarget(@NotNull JpsArtifact artifact) {
    super(ArtifactBuildTargetType.INSTANCE);
    myArtifact = artifact;
  }

  @Override
  public String getId() {
    return myArtifact.getName();
  }

  public JpsArtifact getArtifact() {
    return myArtifact;
  }

  @Override
  public Collection<? extends BuildTarget> computeDependencies() {
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myArtifact.equals(((ArtifactBuildTarget)o).myArtifact);
  }

  @Override
  public int hashCode() {
    return myArtifact.hashCode();
  }

  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId, ModuleRootsIndex index, ArtifactRootsIndex artifactRootsIndex) {
    return artifactRootsIndex.getInstructionsBuilder(myArtifact).getInstructions().get(Integer.valueOf(rootId)).getFirst();
  }
}
