// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.dialog;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public final class ModuleScopeItem implements ModelScopeItem {
  public final Module Module;

  public static @Nullable ModelScopeItem tryCreate(@Nullable Module module) {
    if (module != null) {
      Project project = module.getProject();
      if (ModuleManager.getInstance(project).getModules().length > 1)
        return new ModuleScopeItem(module);
    }
    return null;
  }

  public ModuleScopeItem(Module module) {
    Module = module;
  }

  @Override
  public AnalysisScope getScope() {
    return new AnalysisScope(Module);
  }
}