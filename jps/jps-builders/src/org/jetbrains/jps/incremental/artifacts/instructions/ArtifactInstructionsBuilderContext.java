package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.ProjectPaths;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilderContext {
  @NotNull
  Project getProject();

  @NotNull
  ProjectPaths getProjectPaths();
}
