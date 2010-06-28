/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;

public class CantRunException extends ExecutionException {
  public CantRunException(final String message) {
    super(message);
  }

  public static CantRunException noModuleConfigured(final String moduleName) {
    if (moduleName.trim().length() == 0) {
      return new CantRunException(ExecutionBundle.message("no.module.defined.error.message"));
    }
    return new CantRunException(ExecutionBundle.message("module.does.not.exist.error.message", moduleName));
  }

  public static CantRunException noJdkForModule(@NotNull final Module module) {
    return new CantRunException(ExecutionBundle.message("no.jdk.for.module.error.message", module.getName()));
  }

  public static CantRunException jdkMisconfigured(@NotNull final Sdk jdk, @NotNull final Module module) {
    return new CantRunException(ExecutionBundle.message("jdk.is.bad.configured.error.message", jdk.getName()));
  }

  public static CantRunException classNotFound(@NotNull final String className, @NotNull final Module module) {
    return new CantRunException(ExecutionBundle.message("class.not.found.in.module.error.message", className, module.getName()));
  }

  public static CantRunException packageNotFound(final String packageName) {
    return new CantRunException(ExecutionBundle.message("package.not.found.error.message", packageName));
  }

  public static CantRunException noJdkConfigured(final String jdkName) {
    if (jdkName != null) {
      return new CantRunException(ExecutionBundle.message("jdk.not.configured.error.message", jdkName));
    }
    return new CantRunException(ExecutionBundle.message("project.has.no.jdk.error.message"));
  }

  public static CantRunException badModuleDependencies() {
    return new CantRunException(ExecutionBundle.message("some.modules.has.circular.dependency.error.message"));
  }

  public static CantRunException noJdkConfigured() {
    return new CantRunException(ExecutionBundle.message("project.has.no.jdk.configured.error.message"));
  }
}
