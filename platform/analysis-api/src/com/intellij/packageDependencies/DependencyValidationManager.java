// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class DependencyValidationManager extends NamedScopesHolder {
  @NotNull
  public static DependencyValidationManager getInstance(@NotNull Project project) {
    return project.getService(DependencyValidationManager.class);
  }

  public DependencyValidationManager(@NotNull Project project) {
    super(project);
  }

  public abstract boolean hasRules();

  @Nullable
  public abstract DependencyRule getViolatorDependencyRule(@NotNull PsiFile from, @NotNull PsiFile to);

  public abstract DependencyRule @NotNull [] getViolatorDependencyRules(@NotNull PsiFile from, @NotNull PsiFile to);

  public abstract DependencyRule @NotNull [] getApplicableRules(@NotNull PsiFile file);

  public abstract DependencyRule @NotNull [] getAllRules();

  public abstract void removeAllRules();

  public abstract void addRule(@NotNull DependencyRule rule);

  public abstract boolean skipImportStatements();

  public abstract void setSkipImportStatements(boolean skip);

  @NotNull
  public abstract Map<String, PackageSet> getUnnamedScopes();
}
