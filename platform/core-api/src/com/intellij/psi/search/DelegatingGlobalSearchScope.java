// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.model.ModelBranch;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author peter
 */
public class DelegatingGlobalSearchScope extends GlobalSearchScope {
  protected final GlobalSearchScope myBaseScope;
  private final Object myEquality;

  public DelegatingGlobalSearchScope(@NotNull GlobalSearchScope baseScope) {
    this(baseScope, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
  }

  public DelegatingGlobalSearchScope(@NotNull GlobalSearchScope baseScope, Object @NotNull ... equality) {
    super(baseScope.getProject());
    myBaseScope = baseScope;
    myEquality = equality.length == 0 ? Collections.emptyList() : Arrays.asList(equality);
  }

  public DelegatingGlobalSearchScope(@NotNull Project project, @NotNull GlobalSearchScope baseScope) {
    super(project);
    myBaseScope = baseScope;
    myEquality = Collections.emptyList();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myBaseScope.contains(file);
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return myBaseScope.compare(file1, file2);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myBaseScope.isSearchInModuleContent(aModule);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return myBaseScope.isSearchInModuleContent(aModule, testSources);
  }

  @Override
  public boolean isSearchInLibraries() {
    return myBaseScope.isSearchInLibraries();
  }

  @NotNull
  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return myBaseScope.getUnloadedModulesBelongingToScope();
  }

  @Override
  public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
    return myBaseScope.getModelBranchesAffectingScope();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return myBaseScope.getDisplayName();
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return myBaseScope.getIcon();
  }

  @Override
  public String toString() {
    return getClass().getName() + "[" + myBaseScope + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DelegatingGlobalSearchScope that = (DelegatingGlobalSearchScope)o;

    if (!myBaseScope.equals(that.myBaseScope)) return false;
    if (!myEquality.equals(that.myEquality)) return false;

    return true;
  }

  @Override
  public int calcHashCode() {
    int result = myBaseScope.calcHashCode();
    result = 31 * result + myEquality.hashCode();
    return result;
  }
}
