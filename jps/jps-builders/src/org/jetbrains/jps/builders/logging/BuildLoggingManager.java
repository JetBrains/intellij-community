package org.jetbrains.jps.builders.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerImpl;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLogger;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLoggerImpl;

/**
 * @author nik
 */
public class BuildLoggingManager {
  public static final BuildLoggingManager DEFAULT = new BuildLoggingManager(new ArtifactBuilderLoggerImpl(),
                                                                            new ProjectBuilderLoggerImpl());
  private ArtifactBuilderLogger myArtifactBuilderLogger;
  private final ProjectBuilderLogger myProjectLogger;

  public BuildLoggingManager(@NotNull ArtifactBuilderLogger artifactBuilderLogger,
                             @NotNull ProjectBuilderLogger projectLogger) {
    myArtifactBuilderLogger = artifactBuilderLogger;
    myProjectLogger = projectLogger;
  }

  @NotNull
  public ArtifactBuilderLogger getArtifactBuilderLogger() {
    return myArtifactBuilderLogger;
  }

  @NotNull
  public ProjectBuilderLogger getProjectBuilderLogger() {
    return myProjectLogger;
  }
}
