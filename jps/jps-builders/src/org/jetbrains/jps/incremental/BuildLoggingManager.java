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
  public static final BuildLoggingManager DEFAULT = new BuildLoggingManager(new ArtifactBuilderLoggerImpl(), new JavaBuilderLoggerImpl());
  private ArtifactBuilderLogger myArtifactBuilderLogger;
  private JavaBuilderLogger myJavaBuilderLogger;

  public BuildLoggingManager(@NotNull ArtifactBuilderLogger artifactBuilderLogger, @NotNull JavaBuilderLogger logger) {
    myArtifactBuilderLogger = artifactBuilderLogger;
    myJavaBuilderLogger = logger;
  }

  @NotNull
  public ArtifactBuilderLogger getArtifactBuilderLogger() {
    return myArtifactBuilderLogger;
  }

  @NotNull
  public JavaBuilderLogger getJavaBuilderLogger() {
    return myJavaBuilderLogger;
  }
}
