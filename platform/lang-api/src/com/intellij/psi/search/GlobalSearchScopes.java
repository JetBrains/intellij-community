// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class GlobalSearchScopes {
  private GlobalSearchScopes() {}

  /**
   * @deprecated Please use ExecutionSearchScopes.executionScope
   */
  @Deprecated
  public static @NotNull GlobalSearchScope executionScope(@NotNull Project project, @Nullable RunProfile runProfile) {
    return ExecutionSearchScopes.executionScope(project, runProfile);
  }

  public static @NotNull GlobalSearchScope openFilesScope(@NotNull Project project) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    final VirtualFile[] files = fileEditorManager != null ? fileEditorManager.getOpenFiles() : VirtualFile.EMPTY_ARRAY;
    return GlobalSearchScope.filesScope(project, Arrays.asList(files), IdeBundle.message("scope.open.files"));
  }

  public static @NotNull GlobalSearchScope directoryScope(@NotNull PsiDirectory directory, boolean withSubdirectories) {
    return GlobalSearchScopesCore.directoryScope(directory, withSubdirectories);
  }

  public static @NotNull GlobalSearchScope directoryScope(@NotNull Project project,
                                                          @NotNull VirtualFile directory,
                                                          boolean withSubdirectories) {
    return GlobalSearchScopesCore.directoryScope(project, directory, withSubdirectories);
  }

  public static @NotNull GlobalSearchScope filterScope(@NotNull Project project, @NotNull NamedScope set) {
    return GlobalSearchScopesCore.filterScope(project, set);
  }

  public static @NotNull GlobalSearchScope projectTestScope(@NotNull Project project) {
    return GlobalSearchScopesCore.projectTestScope(project);
  }

  public static @NotNull GlobalSearchScope directoriesScope(@NotNull Project project,
                                                            boolean withSubdirectories,
                                                            VirtualFile @NotNull ... directories) {
    return GlobalSearchScopesCore.directoriesScope(project, withSubdirectories, directories);
  }

  public static @NotNull GlobalSearchScope projectProductionScope(@NotNull Project project) {
    return GlobalSearchScopesCore.projectProductionScope(project);
  }
}
