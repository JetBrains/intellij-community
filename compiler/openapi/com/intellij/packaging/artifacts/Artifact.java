package com.intellij.packaging.artifacts;

import com.intellij.packaging.elements.ArtifactRootElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface Artifact {
  String getName();

  boolean isBuildOnMake();

  @NotNull
  ArtifactRootElement<?> getRootElement();


  String getOutputPath();
}
