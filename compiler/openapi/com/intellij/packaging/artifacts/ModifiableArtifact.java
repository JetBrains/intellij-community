package com.intellij.packaging.artifacts;

import com.intellij.packaging.elements.ArtifactRootElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface ModifiableArtifact extends Artifact {

  void setBuildOnMake(boolean enabled);

  void setOutputPath(String outputPath);

  void setName(@NotNull String name);

  void setRootElement(ArtifactRootElement<?> root);

  void setProperties(ArtifactPropertiesProvider provider, ArtifactProperties<?> properties);

  void setArtifactType(@NotNull ArtifactType selected);

  void setClearOutputDirectoryOnRebuild(boolean clearOutputDirectoryOnRebuild);
}
