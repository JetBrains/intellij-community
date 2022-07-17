// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CantRunException extends ExecutionException {
  public CantRunException(@DialogMessage String message) {
    super(message);
  }

  public CantRunException(@DialogMessage String message, Throwable cause) {
    super(message, cause);
  }

  public static CantRunException noModuleConfigured(@Nullable String moduleName) {
    return StringUtil.isEmptyOrSpaces(moduleName)
           ? new CantRunException(ExecutionBundle.message("no.module.defined.error.message"))
           : new CantRunException(ExecutionBundle.message("module.does.not.exist.error.message", moduleName));
  }

  public static CantRunException noJdkForModule(@NotNull Module module) {
    return new CantRunException(ExecutionBundle.message("no.jdk.for.module.error.message", module.getName()));
  }

  /** @deprecated please use {@link #jdkMisconfigured(Sdk)} instead */
  @Deprecated(forRemoval = true)
  public static CantRunException jdkMisconfigured(@NotNull Sdk jdk, @NotNull Module module) {
    return jdkMisconfigured(jdk);
  }

  public static CantRunException jdkMisconfigured(@NotNull Sdk jdk) {
    return new CantRunException(ExecutionBundle.message("jdk.is.bad.configured.error.message", jdk.getName()));
  }

  public static CantRunException classNotFound(@NotNull String className, @NotNull Module module) {
    return new CantRunException(ExecutionBundle.message("class.not.found.in.module.error.message", className, module.getName()));
  }

  public static CantRunException packageNotFound(@NotNull String packageName) {
    return new CantRunException(ExecutionBundle.message("package.not.found.error.message", packageName));
  }

  public static CantRunException badModuleDependencies() {
    return new CantRunException(ExecutionBundle.message("some.modules.has.circular.dependency.error.message"));
  }

  public static CantRunException noJdkConfigured() {
    return new CantRunException(ExecutionBundle.message("project.has.no.jdk.configured.error.message"));
  }

  /**
   * Exceptions implementing it are skipped and the notification is not shown.
   */
  public static class CustomProcessedCantRunException extends CantRunException {
    public CustomProcessedCantRunException() {
      super("");
    }
  }
}
