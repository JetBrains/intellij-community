// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * Simple override of {@link ModuleWithDependentsTestScope} adding librarys to the scope
 * Created by brian.mcnamara on Jul 26 2018
 **/
public class ModuleWithDependentsAndLibrariesTestScope extends ModuleWithDependentsTestScope {

  ModuleWithDependentsAndLibrariesTestScope(@NotNull Module module) {
    super(new ModuleWithDependentsAndLibrariesScope(module));
  }
}
