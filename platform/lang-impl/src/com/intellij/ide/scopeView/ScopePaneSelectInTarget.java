/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.ide.scopeView;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class ScopePaneSelectInTarget extends ProjectViewSelectInTarget {
  public ScopePaneSelectInTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.SCOPE;
  }

  @Override
  public boolean canSelect(PsiFileSystemItem fileSystemItem) {
    if (!super.canSelect(fileSystemItem)) return false;
    if (!(fileSystemItem instanceof PsiFile)) return false;
    return getContainingScope((PsiFile)fileSystemItem) != null;
  }

  @Nullable
  private NamedScope getContainingScope(@Nullable PsiFile file) {
    if (file == null) return null;
    NamedScopesHolder scopesHolder = DependencyValidationManager.getInstance(myProject);
    for (NamedScope scope : ScopeViewPane.getShownScopes(myProject)) {
      PackageSet packageSet = scope.getValue();
      if (packageSet != null && packageSet.contains(file, scopesHolder)) {
        return scope;
      }
    }
    return null;
  }

  @Override
  public void select(PsiElement element, boolean requestFocus) {
    if (getSubId() == null) {
      NamedScope scope = getContainingScope(element.getContainingFile());
      if (scope == null) return;
      setSubId(scope.getName());
    }
    super.select(element, requestFocus);
  }

  @Override
  public String getMinorViewId() {
    return ScopeViewPane.ID;
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.SCOPE_WEIGHT;
  }

  @Override
  public boolean isSubIdSelectable(@NotNull String subId, @NotNull SelectInContext context) {
    PsiFileSystemItem file = getContextPsiFile(context);
    if (!(file instanceof PsiFile)) return false;
    final NamedScope scope = NamedScopesHolder.getScope(myProject, subId);
    PackageSet packageSet = scope != null ? scope.getValue() : null;
    if (packageSet == null) return false;
    NamedScopesHolder holder = NamedScopesHolder.getHolder(myProject, subId, DependencyValidationManager.getInstance(myProject));
    return packageSet.contains((PsiFile)file, holder);
  }
}
