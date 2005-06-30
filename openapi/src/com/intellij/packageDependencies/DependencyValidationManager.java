package com.intellij.packageDependencies;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;

/**
 * User: anna
 * Date: Mar 2, 2005
 */
public abstract class DependencyValidationManager extends NamedScopesHolder implements ProjectComponent{
  public static DependencyValidationManager getInstance(Project project) {
    return project.getComponent(DependencyValidationManager.class);
  }

  public abstract boolean hasRules();

  public abstract DependencyRule getViolatorDependencyRule(PsiFile from, PsiFile to);

  public abstract DependencyRule[] getViolatorDependencyRules(PsiFile from, PsiFile to);

  public abstract DependencyRule[] getAllRules();

  public abstract void removeAllRules();

  public abstract void addRule(DependencyRule rule);
}
