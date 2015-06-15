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
package com.intellij.packageDependencies;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author anna
 * @since Mar 2, 2005
 */
public abstract class DependencyValidationManager extends NamedScopesHolder {
  public static DependencyValidationManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DependencyValidationManager.class);
  }

  public DependencyValidationManager(@NotNull Project project) {
    super(project);
  }

  public abstract boolean hasRules();

  @Nullable
  public abstract DependencyRule getViolatorDependencyRule(@NotNull PsiFile from, @NotNull PsiFile to);

  @NotNull
  public abstract DependencyRule[] getViolatorDependencyRules(@NotNull PsiFile from, @NotNull PsiFile to);

  @NotNull
  public abstract DependencyRule[] getApplicableRules(@NotNull PsiFile file);

  @NotNull
  public abstract DependencyRule[] getAllRules();

  public abstract void removeAllRules();

  public abstract void addRule(@NotNull DependencyRule rule);

  public abstract boolean skipImportStatements();

  public abstract void setSkipImportStatements(boolean skip);

  @NotNull
  public abstract Map<String, PackageSet> getUnnamedScopes();

  public abstract void reloadRules();
}
