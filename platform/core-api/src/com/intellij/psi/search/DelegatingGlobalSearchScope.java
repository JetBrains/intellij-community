// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  protected DelegatingGlobalSearchScope() {
    myBaseScope = null;
    myEquality = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return getDelegate().contains(file);
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return getDelegate().compare(file1, file2);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return getDelegate().isSearchInModuleContent(aModule);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return getDelegate().isSearchInModuleContent(aModule, testSources);
  }

  @Override
  public boolean isSearchInLibraries() {
    return getDelegate().isSearchInLibraries();
  }

  @NotNull
  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return getDelegate().getUnloadedModulesBelongingToScope();
  }

  @Override
  public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
    return getDelegate().getModelBranchesAffectingScope();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return getDelegate().getDisplayName();
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return getDelegate().getIcon();
  }

  @Override
  public String toString() {
    return getClass().getName() + "[" + getDelegate() + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DelegatingGlobalSearchScope that = (DelegatingGlobalSearchScope)o;

    if (!getDelegate().equals(that.getDelegate())) return false;
    if (!myEquality.equals(that.myEquality)) return false;

    return true;
  }

  @Override
  public int calcHashCode() {
    int result = getDelegate().calcHashCode();
    result = 31 * result + myEquality.hashCode();
    return result;
  }

  @NotNull
  public GlobalSearchScope getDelegate() {
    return myBaseScope;
  }

  @NotNull
  public GlobalSearchScope unwrap() {
    GlobalSearchScope delegate = getDelegate();
    return delegate instanceof DelegatingGlobalSearchScope ? ((DelegatingGlobalSearchScope)delegate).unwrap() : delegate;
  }
}
