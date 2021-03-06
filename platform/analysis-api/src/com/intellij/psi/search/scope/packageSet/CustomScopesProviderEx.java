// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

public abstract class CustomScopesProviderEx implements CustomScopesProvider {
  @Nullable
  public NamedScope getCustomScope(@NotNull String name) {
    final List<NamedScope> predefinedScopes = getFilteredScopes();
    return findPredefinedScope(name, predefinedScopes);
  }

  @Nullable
  public static NamedScope findPredefinedScope(@NotNull String scopeId, @NotNull List<? extends NamedScope> predefinedScopes) {
    for (NamedScope scope : predefinedScopes) {
      if (scopeId.equals(scope.getScopeId())) return scope;
    }
    return null;
  }

  public boolean isVetoed(NamedScope scope, ScopePlace place) {
    return false;
  }

  public static void filterNoSettingsScopes(Project project, List<NamedScope> scopes) {
    for (Iterator<NamedScope> iterator = scopes.iterator(); iterator.hasNext(); ) {
      final NamedScope scope = iterator.next();
      for (CustomScopesProvider provider : CUSTOM_SCOPES_PROVIDER.getExtensions(project)) {
        if (provider instanceof CustomScopesProviderEx && ((CustomScopesProviderEx)provider).isVetoed(scope, ScopePlace.SETTING)) {
          iterator.remove();
          break;
        }
      }
    }
  }

  public enum ScopePlace {
    SETTING, ACTION
  }

  private static class AllScopeHolder {
    @NotNull
    private static final String TEXT = FilePatternPackageSet.SCOPE_FILE + ":*//*";
    @NotNull
    private static final NamedScope ALL = new NamedScope("All", () -> AnalysisBundle.message("all.scope.name"), AllIcons.Ide.LocalScope, new AbstractPackageSet(TEXT, 0) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
        return true;
      }
    });
  }

  @NotNull
  public static NamedScope getAllScope() {
    return AllScopeHolder.ALL;
  }
}
