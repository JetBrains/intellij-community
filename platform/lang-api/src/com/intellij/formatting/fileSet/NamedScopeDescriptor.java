// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.fileSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NamedScopeDescriptor implements FileSetDescriptor {
  public final static String NAMED_SCOPE_TYPE = "namedScope";

  private final String myScopeName;
  private @Nullable PackageSet myFileSet;

  public NamedScopeDescriptor (@NotNull NamedScope scope) {
    myScopeName = scope.getName();
    myFileSet = scope.getValue();
  }

  public NamedScopeDescriptor(@NotNull String scopeName) {
    myScopeName = scopeName;
  }

  public void setPattern(@Nullable String pattern) {
    try {
      myFileSet = pattern != null ? PackageSetFactory.getInstance().compile(pattern) : null;
    }
    catch (ParsingException e) {
      myFileSet = null;
    }
  }

  @Override
  public boolean matches(@NotNull PsiFile psiFile) {
    Pair<NamedScopesHolder,NamedScope> resolved = resolveScope(psiFile.getProject());
    if (resolved == null) {
      resolved = resolveScope(ProjectManager.getInstance().getDefaultProject());
    }
    if (resolved != null) {
      PackageSet fileSet = resolved.second.getValue();
      if (fileSet != null) {
        return fileSet.contains(psiFile, resolved.first);
      }
    }
    if (myFileSet != null) {
      NamedScopesHolder holder = DependencyValidationManager.getInstance(psiFile.getProject());
      return myFileSet.contains(psiFile, holder);
    }
    return false;
  }

  private Pair<NamedScopesHolder,NamedScope> resolveScope(@NotNull Project project) {
    NamedScopesHolder holder = DependencyValidationManager.getInstance(project);
    NamedScope scope = holder.getScope(myScopeName);
    if (scope == null) {
      holder = NamedScopeManager.getInstance(project);
      scope = holder.getScope(myScopeName);
    }
    return scope != null ? Pair.create(holder, scope) : null;
  }

  @NotNull
  @Override
  public String getName() {
    return myScopeName;
  }

  @NotNull
  @Override
  public String getType() {
    return NAMED_SCOPE_TYPE;
  }

  @Nullable
  @Override
  public String getPattern() {
    return myFileSet != null ? myFileSet.getText() : null;
  }

  @Nullable
  public PackageSet getFileSet() {
    return myFileSet;
  }

  @Override
  public String toString() {
    return "scope: " + myScopeName;
  }
}
