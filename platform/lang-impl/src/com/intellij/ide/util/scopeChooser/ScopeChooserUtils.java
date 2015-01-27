/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ChangeListsScopesProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.scope.ProjectFilesScope;
import com.intellij.psi.search.scope.ProjectProductionScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScopeChooserUtils {
  private ScopeChooserUtils() {
  }

  @NotNull
  public static GlobalSearchScope findScopeByName(@NotNull Project project, @Nullable String scopeName) {
    NamedScope namedScope = scopeName == null ? null : ChangeListsScopesProvider.getInstance(project).getCustomScope(scopeName);
    if (namedScope == null) {
      namedScope = NamedScopesHolder.getScope(project, scopeName);
    }
    if (namedScope == null) {
      namedScope = new ProjectFilesScope();
    }
    GlobalSearchScope scope = GlobalSearchScopesCore.filterScope(project, namedScope);
    boolean restrictedToProject = namedScope instanceof ProjectFilesScope || namedScope instanceof ProjectProductionScope;
    if (!restrictedToProject) {
      scope = scope.intersectWith(ProjectScope.getContentScope(project));
    }
    return scope;
  }
}
