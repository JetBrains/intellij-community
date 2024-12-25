// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ModuleSdkPathMacro extends Macro implements PathMacro {
  @Override
  public @NotNull String getName() {
    return "ModuleSdkPath";
  }

  @Override
  public @NotNull String getDescription() {
    return PlatformUtils.isPyCharm()
      ? ExecutionBundle.message("project.interpreter.path")
      : ExecutionBundle.message("module.sdk.path");
  }

  @Override
  public @Nullable String expand(@NotNull DataContext dataContext) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return null;
    }
    return JdkPathMacro.sdkPath(ModuleRootManager.getInstance(module).getSdk());
  }
}