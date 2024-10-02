// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ScopeBasedTodosTreeStructure extends TodoTreeStructure {
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