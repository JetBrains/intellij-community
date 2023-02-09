/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.ide.todo;

import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

public class ScopeBasedTodosTreeStructure extends TodoTreeStructure {
  private final ScopeChooserCombo myScopes;

  public ScopeBasedTodosTreeStructure(Project project, ScopeChooserCombo scopes) {
    super(project);
    myScopes = scopes;
  }

  @Override
  public boolean accept(final @NotNull PsiFile psiFile) {
    if (!psiFile.isValid()) return false;

    SearchScope scope = myScopes.getSelectedScope();
    VirtualFile file = psiFile.getVirtualFile();
    boolean isAffected = scope != null && file != null && scope.contains(file);
    return isAffected && acceptTodoFilter(psiFile);
  }
}