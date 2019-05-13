/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CantRunException extends ExecutionException {
  public CantRunException(final String message) {
    super(message);
  }

  public CantRunException(String s, Throwable cause) {
    super(s, cause);
  }

  public static CantRunException noModuleConfigured(@Nullable String moduleName) {
    if (StringUtil.isEmptyOrSpaces(moduleName)) {
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
