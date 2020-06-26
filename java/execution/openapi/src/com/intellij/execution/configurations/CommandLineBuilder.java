// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;

/** @deprecated use {@link SimpleJavaParameters#toCommandLine()} */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
@SuppressWarnings("unused")
public final class CommandLineBuilder {
  private CommandLineBuilder() { }

  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters) throws CantRunException {
    return javaParameters.toCommandLine();
  }

  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters,
                                                            final Project project,
                                                            final boolean dynamicClasspath) throws CantRunException {
    if (dynamicClasspath) {
      if (!javaParameters.isDynamicClasspath()) {
        javaParameters.setUseDynamicClasspath(project);
      }
    }
    else {
      javaParameters.setUseDynamicClasspath(false);
    }
    return javaParameters.toCommandLine();
  }

  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters,
                                                            final boolean forceDynamicClasspath) throws CantRunException {
    javaParameters.setUseDynamicClasspath(forceDynamicClasspath);
    return javaParameters.toCommandLine();
  }
}