package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.ProjectPaths;

/**
 * @author nik
 */
public class ArtifactInstructionsBuilderContextImpl implements ArtifactInstructionsBuilderContext {
  private final Project myProject;
  private final ProjectPaths myProjectPaths;

  public ArtifactInstructionsBuilderContextImpl(Project project, ProjectPaths projectPaths) {
    myProject = project;
    myProjectPaths = projectPaths;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public ProjectPaths getProjectPaths() {
    return myProjectPaths;
  }
}
