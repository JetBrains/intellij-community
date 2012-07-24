package org.jetbrains.jps.model.artifact;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;

/**
 * @author nik
 */
public interface JpsArtifactReference extends JpsElementReference<JpsArtifact> {
  @NotNull
  String getArtifactName();

  @Override
  JpsArtifactReference asExternal(@NotNull JpsModel model);
}
