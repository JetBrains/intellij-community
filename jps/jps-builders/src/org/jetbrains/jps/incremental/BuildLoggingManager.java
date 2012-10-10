package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLogger;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLoggerImpl;
import org.jetbrains.jps.incremental.java.JavaBuilderLogger;
import org.jetbrains.jps.incremental.java.JavaBuilderLoggerImpl;

/**
 * @author nik
 */
public class BuildLoggingManager {
  public static final BuildLoggingManager DEFAULT = new BuildLoggingManager(new ArtifactBuilderLoggerImpl(), new JavaBuilderLoggerImpl(),
                                                                            new ProjectBuilderLoggerImpl());
  private ArtifactBuilderLogger myArtifactBuilderLogger;
  private JavaBuilderLogger myJavaBuilderLogger;
  private final ProjectBuilderLogger myProjectLogger;

  public BuildLoggingManager(@NotNull ArtifactBuilderLogger artifactBuilderLogger, @NotNull JavaBuilderLogger logger, @NotNull ProjectBuilderLogger projectLogger) {
    myArtifactBuilderLogger = artifactBuilderLogger;
    myJavaBuilderLogger = logger;
    myProjectLogger = projectLogger;
  }

  @NotNull
  public ArtifactBuilderLogger getArtifactBuilderLogger() {
    return myArtifactBuilderLogger;
  }

  @NotNull
  public JavaBuilderLogger getJavaBuilderLogger() {
    return myJavaBuilderLogger;
  }

  @NotNull
  public ProjectBuilderLogger getProjectBuilderLogger() {
    return myProjectLogger;
  }
}
