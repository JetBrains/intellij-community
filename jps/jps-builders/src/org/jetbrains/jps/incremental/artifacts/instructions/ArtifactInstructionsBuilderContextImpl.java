package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class ArtifactInstructionsBuilderContextImpl implements ArtifactInstructionsBuilderContext {
  private final Set<JpsArtifact> myParentArtifacts;
  private final JpsModel myModel;
  private final BuildDataPaths myDataPaths;

  public ArtifactInstructionsBuilderContextImpl(JpsModel model, BuildDataPaths dataPaths) {
    myModel = model;
    myDataPaths = dataPaths;
    myParentArtifacts = new HashSet<JpsArtifact>();
  }

  @Override
  public JpsModel getModel() {
    return myModel;
  }

  @Override
  public BuildDataPaths getDataPaths() {
    return myDataPaths;
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
