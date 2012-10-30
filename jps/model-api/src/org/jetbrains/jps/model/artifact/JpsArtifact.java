package org.jetbrains.jps.model.artifact;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.JpsReferenceableElement;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;

/**
 * @author nik
 */
public interface JpsArtifact extends JpsNamedElement, JpsReferenceableElement<JpsArtifact>, JpsCompositeElement {
  @NotNull
  JpsArtifactType<?> getArtifactType();

  @Nullable
  String getOutputPath();

  void setOutputPath(@Nullable String outputPath);

  @Nullable
  String getOutputFilePath();

  @NotNull
  JpsCompositePackagingElement getRootElement();

  void setRootElement(@NotNull JpsCompositePackagingElement rootElement);

  boolean isBuildOnMake();

  @NotNull
  @Override
  JpsArtifactReference createReference();

  void setBuildOnMake(boolean buildOnMake);

  JpsElement getProperties();
}
