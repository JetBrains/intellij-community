package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsSyntheticArtifactProvider {
  @NotNull
  public abstract List<JpsArtifact> createArtifacts(@NotNull JpsModel model);
}
