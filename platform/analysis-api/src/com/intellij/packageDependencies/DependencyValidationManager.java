// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class DependencyValidationManager extends NamedScopesHolder {
  public static @NotNull DependencyValidationManager getInstance(@NotNull Project project) {
    return project.getService(DependencyValidationManager.class);
  }

  public DependencyValidationManager(@NotNull Project project) {
    super(project);
  }

  public abstract boolean hasRules();

  public abstract @Nullable DependencyRule getViolatorDependencyRule(@NotNull PsiFile from, @NotNull PsiFile to);

  public abstract DependencyRule @NotNull [] getViolatorDependencyRules(@NotNull PsiFile from, @NotNull PsiFile to);

  public abstract DependencyRule @NotNull [] getApplicableRules(@NotNull PsiFile file);

  public abstract DependencyRule @NotNull [] getAllRules();

  public abstract void removeAllRules();

  public abstract void addRule(@NotNull DependencyRule rule);

  public abstract boolean skipImportStatements();

  public abstract void setSkipImportStatements(boolean skip);

  public abstract @NotNull Map<String, PackageSet> getUnnamedScopes();
}
