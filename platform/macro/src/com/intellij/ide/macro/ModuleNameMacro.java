// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

public final class ModuleNameMacro extends Macro {
  @Override
  public @NotNull String getName() {
    return "ModuleName";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.module.file.name");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return null;
    }
    return module.getName();
  }
}
