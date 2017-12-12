/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.analysis.dialog;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;

public class ModuleScopeItem implements ModelScopeItem {
  public final Module Module;

  @Nullable
  public static ModelScopeItem tryCreate(@Nullable Module module) {
    if (module != null)
      return new ModuleScopeItem(module);
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