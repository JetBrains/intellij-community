// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class DebuggerGlobalSearchScope extends DelegatingGlobalSearchScope {
  private final Comparator<VirtualFile> myScopeComparator;
  private final GlobalSearchScope myFallbackAllScope;

  public DebuggerGlobalSearchScope(@NotNull GlobalSearchScope scope, @NotNull Project project) {
    super(project, scope);
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myScopeComparator = Comparator.comparing(projectFileIndex::isInSourceContent)
      .thenComparing(projectFileIndex::isInLibrarySource)
      .thenComparing(super::compare);
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    myFallbackAllScope = !allScope.equals(scope) ? new DebuggerGlobalSearchScope(allScope, project) : null;
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return myScopeComparator.compare(file1, file2);
  }

  public @Nullable Module getModuleIfAny() {
    return myBaseScope instanceof ModuleWithDependenciesScope ? ((ModuleWithDependenciesScope)myBaseScope).getModule() : null;
  }

  public @Nullable GlobalSearchScope fallbackAllScope() {
    return myFallbackAllScope;
  }
}
