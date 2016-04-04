/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * User: anna
 * Date: 3/14/12
 */
public abstract class CustomScopesProviderEx implements CustomScopesProvider {
  @Nullable
  public NamedScope getCustomScope(@NotNull String name) {
    final List<NamedScope> predefinedScopes = getFilteredScopes();
    return findPredefinedScope(name, predefinedScopes);
  }

  @Nullable
  public static NamedScope findPredefinedScope(@NotNull String name, @NotNull List<NamedScope> predefinedScopes) {
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
      for (CustomScopesProvider provider : Extensions.getExtensions(CUSTOM_SCOPES_PROVIDER, project)) {
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

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  private static class AllScopeHolder {
    @NotNull
    private static final String TEXT = FilePatternPackageSet.SCOPE_FILE + ":*//*";
    @NotNull
    private static final NamedScope ALL = new NamedScope("All", new AbstractPackageSet(TEXT, 0) {
      @Override
      public boolean contains(final VirtualFile file, NamedScopesHolder scopesHolder) {
        return true;
      }
    });
  }

  @NotNull
  public static NamedScope getAllScope() {
    return AllScopeHolder.ALL;
  }
}
