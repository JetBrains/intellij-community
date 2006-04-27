/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: Mar 2, 2005
 */
public abstract class DependencyValidationManager extends NamedScopesHolder implements ProjectComponent{
  public static DependencyValidationManager getInstance(Project project) {
    return project.getComponent(DependencyValidationManager.class);
  }

  public abstract NamedScope getProjectScope();

  public abstract NamedScope getProjectTestScope();

  public abstract boolean hasRules();

  public abstract DependencyRule getViolatorDependencyRule(PsiFile from, PsiFile to);

  public abstract @NotNull DependencyRule[] getViolatorDependencyRules(PsiFile from, PsiFile to);

  public abstract @NotNull DependencyRule[] getApplicableRules(PsiFile file);

  public abstract DependencyRule[] getAllRules();

  public abstract void removeAllRules();

  public abstract void addRule(DependencyRule rule);

  public abstract NamedScope getProblemsScope();
}
