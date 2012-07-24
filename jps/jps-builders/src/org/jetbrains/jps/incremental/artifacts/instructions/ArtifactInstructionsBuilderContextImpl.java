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
public class ArtifactInstructionsBuilderContextImpl implements ArtifactInstructionsBuilderContext {
  private final Project myProject;
  private final JpsProject myJpsProject;
  private final ModuleRootsIndex myRootsIndex;
  private final ProjectPaths myProjectPaths;
  private JpsModel myModel;

  public ArtifactInstructionsBuilderContextImpl(Project project, JpsModel jpsModel, ModuleRootsIndex rootsIndex, ProjectPaths projectPaths) {
    myProject = project;
    myJpsProject = jpsModel.getProject();
    myModel = jpsModel;
    myRootsIndex = rootsIndex;
    myProjectPaths = projectPaths;
  }

  @Override
  public ModuleRootsIndex getRootsIndex() {
    return myRootsIndex;
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
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public ProjectPaths getProjectPaths() {
    return myProjectPaths;
  }
}
