// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.scratch.ScratchesNamedScope;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ChangeListsScopesProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.scope.ProjectFilesScope;
import com.intellij.psi.search.scope.ProjectProductionScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScopeChooserUtils {

  private static final String CURRENT_FILE_SCOPE_NAME = IdeBundle.message("scope.current.file");
  private static final String OPEN_FILES_SCOPE_NAME = IdeBundle.message("scope.open.files");

  private ScopeChooserUtils() {
  }

  /**
   * @return custom or standard scope with the provided name; if <code>scopeName</code> is <code>null</code> or matching scope doesn't exist then empty scope is returned
   */
  @NotNull
  public static GlobalSearchScope findScopeByName(@NotNull Project project, @Nullable String scopeName) {
    NamedScope namedScope = scopeName == null ? null : ChangeListsScopesProvider.getInstance(project).getCustomScope(scopeName);
    if (namedScope == null) {
      namedScope = NamedScopesHolder.getScope(project, scopeName);
    }
    if (namedScope == null && OPEN_FILES_SCOPE_NAME.equals(scopeName)) {
      return intersectWithContentScope(project, GlobalSearchScopes.openFilesScope(project));
    }
    if (namedScope == null && CURRENT_FILE_SCOPE_NAME.equals(scopeName)) {
      VirtualFile[] array = FileEditorManager.getInstance(project).getSelectedFiles();
      List<VirtualFile> files = ContainerUtil.createMaybeSingletonList(ArrayUtil.getFirstElement(array));
      GlobalSearchScope scope = GlobalSearchScope.filesScope(project, files, CURRENT_FILE_SCOPE_NAME);
      return intersectWithContentScope(project, scope);
    }
    if (namedScope == null) {
      return GlobalSearchScope.EMPTY_SCOPE;
    }
    GlobalSearchScope scope = GlobalSearchScopesCore.filterScope(project, namedScope);
    if (namedScope instanceof ProjectFilesScope ||
        namedScope instanceof ProjectProductionScope ||
        namedScope instanceof ScratchesNamedScope) {
      return scope;
    }
    return intersectWithContentScope(project, scope);
  }

  @NotNull
  private static GlobalSearchScope intersectWithContentScope(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    return scope.intersectWith(ProjectScope.getContentScope(project));
  }
}
