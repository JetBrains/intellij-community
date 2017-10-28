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
      namedScope = new ProjectFilesScope();
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
