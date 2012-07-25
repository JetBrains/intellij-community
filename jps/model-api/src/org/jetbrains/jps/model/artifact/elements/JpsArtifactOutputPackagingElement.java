package org.jetbrains.jps.model.artifact.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;

/**
 * @author nik
 */
public interface JpsArtifactOutputPackagingElement extends JpsComplexPackagingElement {
  @NotNull
  JpsArtifactReference getArtifactReference();
}
