// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NamedPackageSetReference extends PackageSetBase {
  private final String myName;

  public NamedPackageSetReference(String name) {
    myName = StringUtil.trimStart(name, "$");
  }

  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    if (holder == null) return false;
    NamedScope scope = holder.getScope(myName);

    if (scope == null) {
      if (!(holder instanceof NamedScopeManager)) {
        return false;
      }

      var sharedScopesHolder = DependencyValidationManager.getInstance(project);
      scope = sharedScopesHolder.getScope(myName);
      if (scope == null) {
        return false;
      }
      holder = sharedScopesHolder;
    }

    final PackageSet packageSet = scope.getValue();
    if (packageSet != null) {
      return packageSet instanceof PackageSetBase base
             ? base.contains(file, project, holder)
             : packageSet.contains(getPsiFile(file, project), holder);
    }

    return false;
  }

  @Override
  public @NotNull PackageSet createCopy() {
    return new NamedPackageSetReference(myName);
  }

  @Override
  public @NotNull String getText() {
    return "$" + myName;
  }

  @Override
  public int getNodePriority() {
    return 0;
  }
}
