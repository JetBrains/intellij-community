// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerImpl;

public final class BuildLoggingManager {
  public static final BuildLoggingManager DEFAULT = new BuildLoggingManager(new ProjectBuilderLoggerImpl());
  private final ProjectBuilderLogger myProjectLogger;

  public BuildLoggingManager(@NotNull ProjectBuilderLogger projectLogger) {
    myProjectLogger = projectLogger;
  }

  public @NotNull ProjectBuilderLogger getProjectBuilderLogger() {
    return myProjectLogger;
  }
}
