package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactPointer;
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
    if (myArtifact != null) {
      final Artifact artifact = artifactModel.getArtifactByOriginal(myArtifact);
      if (!artifact.equals(myArtifact)) {
        return artifact;
      }
    }
    return artifactModel.findArtifact(myName);
  }

  void setArtifact(Artifact artifact) {
    myArtifact = artifact;
  }

  void setName(String name) {
    myName = name;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArtifactPointerImpl that = (ArtifactPointerImpl)o;

    if (myArtifact != null ? !myArtifact.equals(that.myArtifact) : that.myArtifact != null) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myProject.equals(that.myProject)) return false;

    return true;
  }

  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + myName.hashCode();
    result = 31 * result + (myArtifact != null ? myArtifact.hashCode() : 0);
    return result;
  }
}
