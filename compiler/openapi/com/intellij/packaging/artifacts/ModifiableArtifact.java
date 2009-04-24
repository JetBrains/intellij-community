package com.intellij.packaging.artifacts;

import com.intellij.packaging.elements.ArtifactRootElement;

/**
 * @author nik
 */
public interface ModifiableArtifact extends Artifact {

  void setBuildOnMake(boolean enabled);

  void setOutputPath(String outputPath);

  void setName(String name);

  void setRootElement(ArtifactRootElement<?> root);
}
