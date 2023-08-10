// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEnumerator;
import org.jetbrains.annotations.NotNull;

public final class ModulePathMacro extends Macro implements PathMacro {
  @NotNull
  @Override
  public String getName() {
    return "ModuleSourcePath";
  }

  @NotNull
  @Override
  public String getDescription() {
    return IdeCoreBundle.message("macro.module.source.path");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return null;
    }
    return OrderEnumerator.orderEntries(module).withoutSdk().withoutLibraries().getSourcePathsList().getPathsString();
  }
}
