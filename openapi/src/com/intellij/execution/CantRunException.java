/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;

public class CantRunException extends ExecutionException {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.CantRunException");

  public CantRunException(final String message) {
    super(message);
  }

  public static CantRunException noModuleConfigured(final String moduleName) {
    if (moduleName.trim().length() == 0) {
      return new CantRunException("No module defined");
    }
    return new CantRunException("Module \"" + moduleName + "\" does not exist");
  }

  public static CantRunException noJdkForModule(final Module module) {
    LOG.assertTrue(module != null);
    return new CantRunException("No jdk for module \"" + module.getName() + "\"");
  }

  public static CantRunException jdkMisconfigured(final ProjectJdk jdk, final Module module) {
    LOG.assertTrue(module != null);
    LOG.assertTrue(jdk != null);
    return new CantRunException("\"" + jdk.getName() + "\" is bad configured");
  }

  public static CantRunException classNotFound(final String className, final Module module) {
    LOG.assertTrue(className != null);
    LOG.assertTrue(module != null);
    return new CantRunException("Class \"" + className + "\" not found in module \"" + module.getName() + "\"");
  }

  public static CantRunException packageNotFound(final String packageName) {
    return new CantRunException("Package \"" + packageName + "\" not found");
  }

  public static CantRunException noJdkConfigured(final String jdkName) {
    if (jdkName != null) {
      return new CantRunException("Jdk \"" + jdkName + "\" not configured");
    }
    return new CantRunException("Project has no JDK");
  }

  public static CantRunException badModuleDependencies() {
    return new CantRunException("Some modules has circular dependency.");
  }

  public static CantRunException noJdkConfigured() {
    return new CantRunException("Project has no JDK configured.");
  }
}
