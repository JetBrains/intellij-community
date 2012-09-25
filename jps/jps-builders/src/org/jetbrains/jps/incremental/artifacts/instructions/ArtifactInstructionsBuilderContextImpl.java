package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;

/**
 * @author nik
 */
public class ArtifactInstructionsBuilderContextImpl implements ArtifactInstructionsBuilderContext {
  private final JpsProject myJpsProject;
  private final ProjectPaths myProjectPaths;
  private JpsModel myModel;

  public ArtifactInstructionsBuilderContextImpl(JpsModel jpsModel, ProjectPaths projectPaths) {
    myJpsProject = jpsModel.getProject();
    myModel = jpsModel;
    myProjectPaths = projectPaths;
  }

  @Override
  public JpsProject getJpsProject() {
    return myJpsProject;
  }

  @Override
  public JpsModel getJpsModel() {
    return myModel;
  }

  @NotNull
  @Override
  public ProjectPaths getProjectPaths() {
    return myProjectPaths;
  }
}
