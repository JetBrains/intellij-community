// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

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
  public static NamedScope findPredefinedScope(@NotNull String name, @NotNull List<? extends NamedScope> predefinedScopes) {
    for (NamedScope scope : predefinedScopes) {
      if (name.equals(scope.getName())) return scope;
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
    private static final NamedScope ALL = new NamedScope("All", new AbstractPackageSet(TEXT, 0) {
      @Override
      public boolean contains(@NotNull final VirtualFile file, NamedScopesHolder scopesHolder) {
        return true;
      }
    });
  }

  @NotNull
  public static NamedScope getAllScope() {
    return AllScopeHolder.ALL;
  }
}
