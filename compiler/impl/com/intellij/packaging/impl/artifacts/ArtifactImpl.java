package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.ArtifactRootElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactImpl implements ModifiableArtifact {
  private ArtifactRootElement<?> myRootElement;
  private String myName;
  private boolean myBuildOnMake;
  private String myOutputPath;

  public ArtifactImpl(String name, boolean buildOnMake, @NotNull ArtifactRootElement<?> rootElement, String outputPath) {
    myName = name;
    myBuildOnMake = buildOnMake;
    myRootElement = rootElement;
    myOutputPath = outputPath;
  }

  public String getName() {
    return myName;
  }

  public boolean isBuildOnMake() {
    return myBuildOnMake;
  }

  @NotNull
  public ArtifactRootElement<?> getRootElement() {
    return myRootElement;
  }

  public String getOutputPath() {
    return myOutputPath;
  }

  public ArtifactImpl createCopy() {
    return new ArtifactImpl(myName, myBuildOnMake, myRootElement, myOutputPath);
  }

  public void setName(String name) {
    myName = name;
  }

  public void setRootElement(ArtifactRootElement<?> root) {
    myRootElement = root;
  }

  public void setBuildOnMake(boolean buildOnMake) {
    myBuildOnMake = buildOnMake;
  }

  public void setOutputPath(String outputPath) {
    myOutputPath = outputPath;
  }
}
