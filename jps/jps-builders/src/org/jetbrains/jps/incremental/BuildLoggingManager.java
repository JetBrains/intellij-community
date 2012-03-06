package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLogger;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLoggerImpl;

/**
 * @author nik
 */
public class BuildLoggingManager {
  public static final BuildLoggingManager DEFAULT = new BuildLoggingManager(new ArtifactBuilderLoggerImpl());
  private ArtifactBuilderLogger myArtifactBuilderLogger;

  public BuildLoggingManager(@NotNull ArtifactBuilderLogger artifactBuilderLogger) {
    myArtifactBuilderLogger = artifactBuilderLogger;
  }

  @NotNull
  public ArtifactBuilderLogger getArtifactBuilderLogger() {
    return myArtifactBuilderLogger;
  }
}
