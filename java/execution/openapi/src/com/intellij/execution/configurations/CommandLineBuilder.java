/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.openapi.project.Project;

public class CommandLineBuilder {
  private CommandLineBuilder() { }

  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters) throws CantRunException {
    return javaParameters.toCommandLine();
  }

  /**
   * In order to avoid too long cmd problem dynamic classpath can be used - if allowed by both  {@code dynamicClasspath} parameter
   * and project settings.
   *
   * @param javaParameters   parameters.
   * @param project          a project to get a dynamic classpath setting from.
   * @param dynamicClasspath whether system properties and project settings will be able to cause using dynamic classpath. If false,
   *                         classpath will always be passed through the command line.
   * @return a command line.
   * @throws CantRunException if there are problems with JDK setup.
   */
  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters,
                                                            final Project project,
                                                            final boolean dynamicClasspath) throws CantRunException {
    if (dynamicClasspath) {
      javaParameters.setUseDynamicClasspath(project);
    }
    else {
      javaParameters.setUseDynamicClasspath(false);
    }
    return javaParameters.toCommandLine();
  }

  /**
   * @param javaParameters        parameters.
   * @param forceDynamicClasspath whether dynamic classpath will be used for this execution, to prevent problems caused by too long command line.
   * @return a command line.
   * @throws CantRunException if there are problems with JDK setup.
   */
  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters,
                                                            final boolean forceDynamicClasspath) throws CantRunException {
    javaParameters.setUseDynamicClasspath(forceDynamicClasspath);
    return javaParameters.toCommandLine();
  }
}