// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.ActualCodeInsightContextInfo;
import com.intellij.psi.search.CodeInsightContextFileInfo;
import com.intellij.psi.search.CodeInsightContextInfo;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

// Tests only (module plus dependencies) scope
// Delegates to ModuleWithDependentsScope with extra flag testOnly to reduce memory for holding modules and CPU for traversing dependencies.
final class ModuleWithDependentsTestScope extends DelegatingGlobalSearchScope implements ActualCodeInsightContextInfo {
  ModuleWithDependentsTestScope(@NotNull Module module) {
    // the additional equality argument allows to distinguish ModuleWithDependentsTestScope from ModuleWithDependentsScope
    super(new ModuleWithDependentsScope(module), true);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    ModuleWithDependentsScope scope = getBaseScope();
    return scope.contains(file, CodeInsightContexts.anyContext(), true);
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull CodeInsightContextInfo getCodeInsightContextInfo() {
    return this;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull CodeInsightContext context) {
    ModuleWithDependentsScope scope = getBaseScope();
    return scope.contains(file, context, true);
  }

  @Override
  public @NotNull CodeInsightContextFileInfo getFileInfo(@NotNull VirtualFile file) {
    ModuleWithDependentsScope scope = getBaseScope();
    return scope.getFileInfo(file, true);
  }

  private @NotNull ModuleWithDependentsScope getBaseScope() {
    return (ModuleWithDependentsScope)getDelegate();
  }

  @Override
  public String toString() {
    return "Restricted by tests: (" + myBaseScope + ")";
  }
}
