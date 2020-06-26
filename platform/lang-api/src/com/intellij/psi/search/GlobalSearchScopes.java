// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.SearchScopeProvidingRunProfile;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public final class GlobalSearchScopes extends GlobalSearchScopesCore {

  private GlobalSearchScopes() {}

  @NotNull
  public static GlobalSearchScope openFilesScope(@NotNull Project project) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    final VirtualFile[] files = fileEditorManager != null ? fileEditorManager.getOpenFiles() : VirtualFile.EMPTY_ARRAY;
    return GlobalSearchScope.filesScope(project, Arrays.asList(files), IdeBundle.message("scope.open.files"));
  }

  @NotNull
  public static GlobalSearchScope executionScope(@NotNull Project project, @Nullable RunProfile runProfile) {
    if (runProfile instanceof SearchScopeProvidingRunProfile) {
      GlobalSearchScope scope = ((SearchScopeProvidingRunProfile)runProfile).getSearchScope();
      if (scope != null) return scope;
    }
    return GlobalSearchScope.allScope(project);
  }

  @Nullable
  public static GlobalSearchScope executionScope(@NotNull Collection<? extends Module> modules) {
    if (modules.isEmpty()) return null;
    List<GlobalSearchScope> scopes = ContainerUtil.map2List(
      modules, module -> GlobalSearchScope.moduleRuntimeScope(module, true));
    return GlobalSearchScope.union(scopes);
  }
}
