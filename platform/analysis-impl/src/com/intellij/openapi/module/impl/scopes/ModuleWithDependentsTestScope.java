// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import org.jetbrains.annotations.NotNull;

// Tests only (module plus dependencies) scope
// Delegates to ModuleWithDependentsScope with extra flag testOnly to reduce memory for holding modules and CPU for traversing dependencies.
class ModuleWithDependentsTestScope extends DelegatingGlobalSearchScope {
  ModuleWithDependentsTestScope(@NotNull Module module) {
    // the additional equality argument allows to distinguish ModuleWithDependentsTestScope from ModuleWithDependentsScope
    super(new ModuleWithDependentsScope(module), true);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return ((ModuleWithDependentsScope)getDelegate()).contains(file, true);
  }

  @Override
  public String toString() {
    return "Restricted by tests: (" + myBaseScope + ")";
  }
}
