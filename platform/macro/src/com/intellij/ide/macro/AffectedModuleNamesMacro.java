// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public final class AffectedModuleNamesMacro extends Macro {
  @Override
  public @NotNull String getName() {
    return "AffectedModuleNames";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.affected.module.names");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modules == null) {
      return null;
    }
    return StringUtil.join(modules, Module::getName, ",");
  }
}