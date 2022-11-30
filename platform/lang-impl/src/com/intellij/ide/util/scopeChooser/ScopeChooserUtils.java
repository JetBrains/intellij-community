// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.scopeChooser;

import com.intellij.find.impl.FindInProjectExtension;
import com.intellij.ide.scratch.ScratchesSearchScope;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.OpenFilesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.*;
import com.intellij.psi.search.scope.ProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ScopeChooserUtils {
  private ScopeChooserUtils() {
  }

  /**
   * @return custom or standard scope with the provided name, i.e. scope that matches corresponding item from {@link ScopeChooserCombo}
   * with the following limitations:
   * <ul>
   * <li>module-specific scope is not handled: if <code>scopePresentableName</code> is "Module 'foo'" then {@link ProjectFilesScope} is returned</li>
   * <li>each returned scope is intersected with the project content (the only exception is 'Scratches and Consoles' scope)</li>
   * <li>if no known scope with the provided name found then empty scope is returned</li>
   * </ul>
   */
  public static @NotNull GlobalSearchScope findScopeByName(@NotNull Project project,
                                                           @Nullable String scopePresentableName) {
    // logic here is similar to ScopeChooserCombo

    if (scopePresentableName == null) return GlobalSearchScope.EMPTY_SCOPE;

    if (OpenFilesScope.getNameText().equals(scopePresentableName)) {
      return intersectWithContentScope(project, GlobalSearchScopes.openFilesScope(project));
    }

    if (PredefinedSearchScopeProviderImpl.getCurrentFileScopeName().equals(scopePresentableName)) {
      VirtualFile[] array = FileEditorManager.getInstance(project).getSelectedFiles();
      List<VirtualFile> files = ContainerUtil.createMaybeSingletonList(ArrayUtil.getFirstElement(array));
      GlobalSearchScope scope = GlobalSearchScope.filesScope(project, files, PredefinedSearchScopeProviderImpl.getCurrentFileScopeName());
      return intersectWithContentScope(project, scope);
    }

    PredefinedSearchScopeProvider scopeProvider = PredefinedSearchScopeProvider.getInstance();
    for (SearchScope scope : scopeProvider.getPredefinedScopes(project, null, false, false, false, false, true)) {
      if (scope instanceof GlobalSearchScope && scope.getDisplayName().equals(scopePresentableName)) {
        if (scope instanceof ScratchesSearchScope) {
          return (ScratchesSearchScope)scope;
        }
        return intersectWithContentScope(project, (GlobalSearchScope)scope);
      }
    }

    for (FindInProjectExtension extension : FindInProjectExtension.EP_NAME.getExtensionList()) {
      for (NamedScope scope : extension.getFilteredNamedScopes(project)) {
        if (scope.getPresentableName().equals(scopePresentableName)) {
          return intersectWithContentScope(project, GlobalSearchScopesCore.filterScope(project, scope));
        }
      }
    }

    for (NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(project)) {
      final NamedScope[] scopes = holder.getEditableScopes();  // predefined scopes already included
      for (NamedScope scope : scopes) {
        if (scope.getScopeId().equals(scopePresentableName)) {
          return intersectWithContentScope(project, GlobalSearchScopesCore.filterScope(project, scope));
        }
      }
    }

    if (scopePresentableName.startsWith("Module '") && scopePresentableName.endsWith("'")) {
      // Backward compatibility with previous File Watchers behavior.
      // It never worked correctly for scopes like "Module 'foo'" and always returned ProjectFilesScope in such cases.
      return ProjectScope.getContentScope(project);
    }

    return GlobalSearchScope.EMPTY_SCOPE;
  }

  private static @NotNull GlobalSearchScope intersectWithContentScope(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    return scope.intersectWith(ProjectScope.getContentScope(project));
  }
}
