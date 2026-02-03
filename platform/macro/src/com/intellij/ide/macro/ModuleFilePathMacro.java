// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

public final class ModuleFilePathMacro extends Macro implements PathMacro {
  @Override
  public @NotNull String getName() {
    return "ModuleFilePath";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.module.file.path");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    return module != null ? module.getModuleFilePath() : null;
  }
}
