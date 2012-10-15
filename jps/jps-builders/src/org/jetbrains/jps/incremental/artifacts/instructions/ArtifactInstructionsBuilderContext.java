package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilderContext {

  boolean enterArtifact(JpsArtifact artifact);

  void leaveArtifact(JpsArtifact artifact);

  BuildDataPaths getDataPaths();

  JpsModel getModel();
}
