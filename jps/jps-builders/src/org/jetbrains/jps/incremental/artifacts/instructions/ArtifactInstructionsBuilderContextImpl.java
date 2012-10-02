package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class ArtifactInstructionsBuilderContextImpl implements ArtifactInstructionsBuilderContext {
  private final Set<JpsArtifact> myParentArtifacts;

  public ArtifactInstructionsBuilderContextImpl() {
    myParentArtifacts = new HashSet<JpsArtifact>();
  }

  @Override
  public boolean enterArtifact(JpsArtifact artifact) {
    return myParentArtifacts.add(artifact);
  }

  @Override
  public void leaveArtifact(JpsArtifact artifact) {
    myParentArtifacts.remove(artifact);
  }
}
