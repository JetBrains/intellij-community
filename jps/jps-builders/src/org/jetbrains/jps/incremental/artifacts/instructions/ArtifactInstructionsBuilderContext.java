package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;

/**
 * @author nik
 */
public interface ArtifactInstructionsBuilderContext {
  @NotNull
  Project getProject();

  @NotNull
  ProjectPaths getProjectPaths();

  ModuleRootsIndex getRootsIndex();

  JpsProject getJpsProject();

  JpsModel getJpsModel();
}
