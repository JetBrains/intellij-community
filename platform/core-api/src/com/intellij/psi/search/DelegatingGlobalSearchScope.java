// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class DelegatingGlobalSearchScope extends GlobalSearchScope implements CodeInsightContextAwareSearchScope {
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

  protected DelegatingGlobalSearchScope(@NotNull Project project) {
    super(project);
    myBaseScope = null;
    myEquality = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull CodeInsightContextInfo getCodeInsightContextInfo() {
    GlobalSearchScope delegate = getDelegate();
    return delegate instanceof CodeInsightContextAwareSearchScope
           ? ((CodeInsightContextAwareSearchScope)delegate).getCodeInsightContextInfo()
           : CodeInsightContextAwareSearchScopes.NoContextInformation();
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

  @Override
  public @Unmodifiable @NotNull Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return getDelegate().getUnloadedModulesBelongingToScope();
  }

  @Override
  public @NotNull String getDisplayName() {
    return getDelegate().getDisplayName();
  }

  @Override
  public @Nullable Icon getIcon() {
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

  public @NotNull GlobalSearchScope getDelegate() {
    return myBaseScope;
  }

  public @NotNull GlobalSearchScope unwrap() {
    GlobalSearchScope delegate = getDelegate();
    return delegate instanceof DelegatingGlobalSearchScope ? ((DelegatingGlobalSearchScope)delegate).unwrap() : delegate;
  }
}
