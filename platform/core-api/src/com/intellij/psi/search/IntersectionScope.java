// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumerationAware;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collection;

final class IntersectionScope extends GlobalSearchScope implements VirtualFileEnumerationAware, CodeInsightContextAwareSearchScope {
  final GlobalSearchScope myScope1;
  final GlobalSearchScope myScope2;

  IntersectionScope(@NotNull GlobalSearchScope scope1, @NotNull GlobalSearchScope scope2) {
    super(scope1.getProject() == null ? scope2.getProject() : scope1.getProject());
    myScope1 = scope1;
    myScope2 = scope2;
  }

  @Override
  public @NotNull GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    return containsScope(scope) ? this : new IntersectionScope(this, scope);
  }

  boolean containsScope(@NotNull GlobalSearchScope scope) {
    if (myScope1.equals(scope) || myScope2.equals(scope) || equals(scope)) return true;
    if (myScope1 instanceof IntersectionScope && ((IntersectionScope)myScope1).containsScope(scope)) return true;
    return myScope2 instanceof IntersectionScope && ((IntersectionScope)myScope2).containsScope(scope);
  }

  @Override
  public @NotNull String getDisplayName() {
    return CoreBundle.message("psi.search.scope.intersection", myScope1.getDisplayName(), myScope2.getDisplayName());
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myScope1.contains(file) && myScope2.contains(file);
  }

  @Override
  public @NotNull CodeInsightContextInfo getCodeInsightContextInfo() {
    return CodeInsightContextInfoIntersectionKt.createIntersectionCodeInsightContextInfo(myScope1, myScope2);
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    int res1 = myScope1.compare(file1, file2);
    int res2 = myScope2.compare(file1, file2);

    if (res1 == 0) return res2;
    if (res2 == 0) return res1;

    if (res1 > 0 == res2 > 0) return res1;

    return 0;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myScope1.isSearchInModuleContent(aModule) && myScope2.isSearchInModuleContent(aModule);
  }

  @Override
  public boolean isSearchInModuleContent(final @NotNull Module aModule, final boolean testSources) {
    return myScope1.isSearchInModuleContent(aModule, testSources) && myScope2.isSearchInModuleContent(aModule, testSources);
  }

  @Override
  public boolean isSearchInLibraries() {
    return myScope1.isSearchInLibraries() && myScope2.isSearchInLibraries();
  }

  @Override
  public @Unmodifiable @NotNull Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return ContainerUtil.intersection(myScope1.getUnloadedModulesBelongingToScope(), myScope2.getUnloadedModulesBelongingToScope());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IntersectionScope)) return false;

    IntersectionScope that = (IntersectionScope)o;

    return myScope1.equals(that.myScope1) && myScope2.equals(that.myScope2);
  }

  @Override
  public int calcHashCode() {
    return 31 * myScope1.hashCode() + myScope2.hashCode();
  }

  @Override
  public @NonNls String toString() {
    return "Intersection: (" + myScope1 + ", " + myScope2 + ")";
  }

  @Override
  public @Nullable VirtualFileEnumeration extractFileEnumeration() {
    VirtualFileEnumeration fileEnumeration1 = VirtualFileEnumeration.extract(myScope1);
    VirtualFileEnumeration fileEnumeration2 = VirtualFileEnumeration.extract(myScope2);
    if (fileEnumeration1 == null) return null;
    if (fileEnumeration2 == null) return null;
    return new IntersectionFileEnumeration(Arrays.asList(fileEnumeration1, fileEnumeration2));
  }
}
