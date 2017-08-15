/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

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

  @NotNull
  @Override
  public String getDisplayName() {
    return myBaseScope.getDisplayName();
  }

  @Override
  public String toString() {
    return getClass().toString() + " delegating to " + myBaseScope.toString();
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
