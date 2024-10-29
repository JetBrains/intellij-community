// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.formatting.fileSet.FileSetDescriptor;
import com.intellij.formatting.fileSet.FileSetDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NamedScopeDescriptor implements FileSetDescriptor {
  public static final String NAMED_SCOPE_TYPE = "namedScope";

  private final String myScopeId;
  private @Nullable PackageSet myFileSet;

  public NamedScopeDescriptor (@NotNull NamedScope scope) {
    myScopeId = scope.getScopeId();
    myFileSet = scope.getValue();
  }

  public NamedScopeDescriptor(@NotNull String scopeId) {
    myScopeId = scopeId;
  }

  @Override
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
    NamedScope scope = holder.getScope(myScopeId);
    if (scope == null) {
      holder = NamedScopeManager.getInstance(project);
      scope = holder.getScope(myScopeId);
    }
    return scope != null ? Pair.create(holder, scope) : null;
  }

  @Override
  public @NotNull String getName() {
    return myScopeId;
  }

  @Override
  public @NotNull String getType() {
    return NAMED_SCOPE_TYPE;
  }

  @Override
  public @Nullable String getPattern() {
    return myFileSet != null ? myFileSet.getText() : null;
  }

  public @Nullable PackageSet getFileSet() {
    return myFileSet;
  }

  @Override
  public String toString() {
    return "scope: " + myScopeId;
  }

  public static final class Factory implements FileSetDescriptorFactory {

    @Override
    public @Nullable FileSetDescriptor createDescriptor(@NotNull State state) {
      if (NAMED_SCOPE_TYPE.equals(state.type) && state.name != null) {
        NamedScopeDescriptor descriptor = new NamedScopeDescriptor(state.name);
        if (state.pattern != null) {
          descriptor.setPattern(state.pattern);
        }
        return descriptor;
      }
      return null;
    }
  }
}
