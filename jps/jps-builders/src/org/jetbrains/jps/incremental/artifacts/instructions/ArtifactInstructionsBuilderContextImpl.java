package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class ArtifactInstructionsBuilderContextImpl implements ArtifactInstructionsBuilderContext {
  private final JpsProject myJpsProject;
  private final ProjectPaths myProjectPaths;
  private final Set<JpsArtifact> myParentArtifacts;
  private JpsModel myModel;

  public ArtifactInstructionsBuilderContextImpl(JpsModel jpsModel, ProjectPaths projectPaths) {
    myJpsProject = jpsModel.getProject();
    myModel = jpsModel;
    myProjectPaths = projectPaths;
    myParentArtifacts = new HashSet<JpsArtifact>();
  }

  @Override
  public JpsProject getJpsProject() {
    return myJpsProject;
  }

  @Override
  public JpsModel getJpsModel() {
    return myModel;
  }

  @Override
  public boolean enterArtifact(JpsArtifact artifact) {
    return myParentArtifacts.add(artifact);
  }

  @Override
  public void leaveArtifact(JpsArtifact artifact) {
    myParentArtifacts.remove(artifact);
  }

  @NotNull
  @Override
  public ProjectPaths getProjectPaths() {
    return myProjectPaths;
  }
}
