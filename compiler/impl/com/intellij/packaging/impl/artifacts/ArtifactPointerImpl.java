package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactPointerImpl implements ArtifactPointer {
  private final Project myProject;
  private String myName;
  private Artifact myArtifact;

  public ArtifactPointerImpl(@NotNull Project project, @NotNull String name) {
    myProject = project;
    myName = name;
  }

  public ArtifactPointerImpl(@NotNull Project project, @NotNull Artifact artifact) {
    myProject = project;
    myArtifact = artifact;
    myName = artifact.getName();
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public Artifact getArtifact() {
    if (myArtifact == null) {
      myArtifact = findArtifact(ArtifactManager.getInstance(myProject));
    }
    return myArtifact;
  }

  public Artifact findArtifact(@NotNull ArtifactModel artifactModel) {
    return artifactModel.findArtifact(myName);
  }

  void setArtifact(Artifact artifact) {
    myArtifact = artifact;
  }

  void setName(String name) {
    myName = name;
  }
}
