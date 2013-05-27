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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.util.ArrayUtil;

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
    PsiFile file = (PsiFile) fileSystemItem;
    NamedScopesHolder scopesHolder = DependencyValidationManager.getInstance(myProject);
    NamedScope[] allScopes = scopesHolder.getScopes();
    allScopes = ArrayUtil.mergeArrays(allScopes, NamedScopeManager.getInstance(myProject).getScopes());
    for (NamedScope scope : allScopes) {
      PackageSet packageSet = scope.getValue();
      if (packageSet != null && packageSet.contains(file, scopesHolder)) return true;
    }
    return false;
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
  protected boolean canWorkWithCustomObjects() {
    return false;
  }

  @Override
  public boolean isSubIdSelectable(String subId, SelectInContext context) {
    if (context == null) return false;
    final NamedScope scope = NamedScopesHolder.getScope(myProject, subId);
    if (scope == null) return false;
    PackageSet packageSet = scope.getValue();
    final VirtualFile virtualFile = context.getVirtualFile();
    if (packageSet != null) {
      final NamedScopesHolder holder = NamedScopesHolder.getHolder(myProject, subId, DependencyValidationManager.getInstance(myProject));
      if (packageSet instanceof PackageSetBase ? ((PackageSetBase)packageSet).contains(virtualFile, myProject, holder) : packageSet.contains(PackageSetBase.getPsiFile(virtualFile, myProject), holder)) {
        return true;
      }
    }
    return false;
  }
}
