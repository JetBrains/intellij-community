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

/** @deprecated use {@link SimpleJavaParameters#toCommandLine()} (to be removed in IDEA 2019) */
@Deprecated
@SuppressWarnings("unused")
public class CommandLineBuilder {
  private CommandLineBuilder() { }

  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters) throws CantRunException {
    return javaParameters.toCommandLine();
  }

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

  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters,
                                                            final boolean forceDynamicClasspath) throws CantRunException {
    javaParameters.setUseDynamicClasspath(forceDynamicClasspath);
    return javaParameters.toCommandLine();
  }
}