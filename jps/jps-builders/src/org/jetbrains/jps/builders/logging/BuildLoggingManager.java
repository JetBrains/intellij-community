/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
