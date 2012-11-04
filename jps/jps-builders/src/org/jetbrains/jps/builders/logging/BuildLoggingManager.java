package org.jetbrains.jps.builders.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerImpl;

/**
 * @author nik
 */
public class BuildLoggingManager {
  public static final BuildLoggingManager DEFAULT = new BuildLoggingManager(new ProjectBuilderLoggerImpl());
  private final ProjectBuilderLogger myProjectLogger;

  public BuildLoggingManager(@NotNull ProjectBuilderLogger projectLogger) {
    myProjectLogger = projectLogger;
  }

  @NotNull
  public ProjectBuilderLogger getProjectBuilderLogger() {
    return myProjectLogger;
  }
}
