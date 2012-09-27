package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilderContext {

  @NotNull
  ProjectPaths getProjectPaths();

  JpsProject getJpsProject();

  JpsModel getJpsModel();

  boolean enterArtifact(JpsArtifact artifact);

  void leaveArtifact(JpsArtifact artifact);
}
