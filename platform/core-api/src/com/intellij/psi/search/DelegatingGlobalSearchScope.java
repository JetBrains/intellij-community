// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author peter
 */
public class DelegatingGlobalSearchScope extends GlobalSearchScope {
  protected final GlobalSearchScope myBaseScope;
  private final Object myEquality;

  public DelegatingGlobalSearchScope(@NotNull GlobalSearchScope baseScope) {
    this(baseScope, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public DelegatingGlobalSearchScope(@NotNull GlobalSearchScope baseScope, @NotNull Object... equality) {
    super(baseScope.getProject());
    myBaseScope = baseScope;
    myEquality = Arrays.asList(equality);
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

  @Override
  public boolean isSearchOutsideRootModel() {
    return myBaseScope.isSearchOutsideRootModel();
  }

  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return myBaseScope.getUnloadedModulesBelongingToScope();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return myBaseScope.getDisplayName();
  }

  @Nullable
  @Override
  public Icon getDisplayIcon() {
    return myBaseScope.getDisplayIcon();
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
  public int hashCode() {
    int result = myBaseScope.hashCode();
    result = 31 * result + myEquality.hashCode();
    return result;
  }
}
