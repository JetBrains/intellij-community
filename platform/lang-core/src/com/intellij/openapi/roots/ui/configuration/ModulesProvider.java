// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.RootModelProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ModulesProvider extends RootModelProvider {
  ModulesProvider EMPTY_MODULES_PROVIDER = new ModulesProvider() {
    @Override
    public Module @NotNull [] getModules() {
      return Module.EMPTY_ARRAY;
    }
    @Override
    public Module getModule(@NotNull String name) {
      return null;
    }

    @Override
    public ModuleRootModel getRootModel(@NotNull Module module) {
      return ModuleRootManager.getInstance(module);
    }

    @NotNull
    @Override
    public FacetModel getFacetModel(@NotNull Module module) {
      return FacetManager.getInstance(module);
    }
  };

  @Nullable
  Module getModule(@NotNull String name);

  @NotNull
  FacetModel getFacetModel(@NotNull Module module);
}
