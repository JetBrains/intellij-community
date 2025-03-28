// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumerationAware;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

final class UnionScope extends GlobalSearchScope implements VirtualFileEnumerationAware, CodeInsightContextAwareSearchScope {
  final GlobalSearchScope @NotNull [] myScopes;

  @Override
  public @Nullable VirtualFileEnumeration extractFileEnumeration() {
    List<VirtualFileEnumeration> fileEnumerations = new SmartList<>();
    for (GlobalSearchScope scope : myScopes) {
      VirtualFileEnumeration fileEnumeration = VirtualFileEnumeration.extract(scope);
      if (fileEnumeration == null) {
        return null;
      }
      fileEnumerations.add(fileEnumeration);
    }
    return new UnionFileEnumeration(fileEnumerations);
  }

  static @NotNull GlobalSearchScope create(GlobalSearchScope @NotNull [] scopes) {
    if (scopes.length == 2) {
      GlobalSearchScope unionScope = tryCreateUnionFor2Scopes(scopes);
      if (unionScope != null) return unionScope;
    }
    Set<GlobalSearchScope> result = new HashSet<>(scopes.length);
    Project project = null;
    for (GlobalSearchScope scope : scopes) {
      if (scope == EMPTY_SCOPE) continue;
      Project scopeProject = scope.getProject();
      if (scopeProject != null) project = scopeProject;
      if (scope instanceof UnionScope) {
        ContainerUtil.addAll(result, ((UnionScope)scope).myScopes);
      }
      else {
        result.add(scope);
      }
    }
    if (result.isEmpty()) return EMPTY_SCOPE;
    if (result.size() == 1) return result.iterator().next();
    return new UnionScope(project, result.toArray(EMPTY_ARRAY));
  }

  private static @Nullable GlobalSearchScope tryCreateUnionFor2Scopes(GlobalSearchScope @NotNull [] scopes) {
    assert scopes.length == 2;
    GlobalSearchScope scope0 = scopes[0];
    GlobalSearchScope scope1 = scopes[1];
    if (scope0 == EMPTY_SCOPE) return scope1;
    if (scope1 == EMPTY_SCOPE) return scope0;
    if (scope0 instanceof UnionScope && scope1 instanceof UnionScope) return null;
    Project project = ObjectUtils.chooseNotNull(scope0.getProject(), scope1.getProject());

    if (scope0 instanceof UnionScope) {
      return unionWithUnionScope(scope0, scope1, project);
    }

    if (scope1 instanceof UnionScope) {
      return unionWithUnionScope(scope1, scope0, project);
    }

    return new UnionScope(project, scopes);
  }

  private static @NotNull GlobalSearchScope unionWithUnionScope(GlobalSearchScope scope0, GlobalSearchScope scope1, Project project) {
    GlobalSearchScope[] scopes0 = ((UnionScope)scope0).myScopes;
    if (ArrayUtil.contains(scope1, scopes0)) {
      return scope0;
    }
    else {
      return new UnionScope(project, ArrayUtil.append(scopes0, scope1));
    }
  }

  private UnionScope(Project project, GlobalSearchScope @NotNull [] scopes) {
    super(project);
    myScopes = scopes;
    if (scopes.length < 2) {
      throw new IllegalArgumentException("expected >= 2 scopes but got: " + Arrays.toString(scopes));
    }
  }

  @Override
  public @NotNull String getDisplayName() {
    return CoreBundle.message("psi.search.scope.union", myScopes[0].getDisplayName(), myScopes[1].getDisplayName());
  }

  @Override
  public boolean contains(final @NotNull VirtualFile file) {
    return ContainerUtil.find(myScopes, scope -> scope.contains(file)) != null;
  }

  @Override
  public @NotNull CodeInsightContextInfo getCodeInsightContextInfo() {
    return CodeInsightContextInfoUnionKt.createCodeInsightContextInfoUnion(myScopes);
  }

  @Override
  public @NotNull @Unmodifiable Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    Set<UnloadedModuleDescription> result = new LinkedHashSet<>();
    for (GlobalSearchScope scope : myScopes) {
      result.addAll(scope.getUnloadedModulesBelongingToScope());
    }
    return result;
  }

  @Override
  public int compare(final @NotNull VirtualFile file1, final @NotNull VirtualFile file2) {
    final int[] result = {0};
    ContainerUtil.process(myScopes, scope -> {
      // ignore irrelevant scopes - they don't know anything about the files
      if (!scope.contains(file1) || !scope.contains(file2)) return true;
      int cmp = scope.compare(file1, file2);
      if (result[0] == 0) {
        result[0] = cmp;
        return true;
      }
      if (cmp == 0) {
        return true;
      }
      if (result[0] > 0 == cmp > 0) {
        return true;
      }
      // scopes disagree about the order - abort the voting
      result[0] = 0;
      return false;
    });
    return result[0];
  }

  @Override
  public boolean isSearchInModuleContent(final @NotNull Module module) {
    return ContainerUtil.find(myScopes, scope -> scope.isSearchInModuleContent(module)) != null;
  }

  @Override
  public boolean isSearchInModuleContent(final @NotNull Module module, final boolean testSources) {
    return ContainerUtil.find(myScopes, scope -> scope.isSearchInModuleContent(module, testSources)) != null;
  }

  @Override
  public boolean isSearchInLibraries() {
    return ContainerUtil.find(myScopes, GlobalSearchScope::isSearchInLibraries) != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UnionScope)) return false;

    UnionScope that = (UnionScope)o;

    return ContainerUtil.newHashSet(myScopes).equals(ContainerUtil.newHashSet(that.myScopes));
  }

  @Override
  public int calcHashCode() {
    return Arrays.hashCode(myScopes);
  }

  @Override
  public @NonNls String toString() {
    return "Union: (" + StringUtil.join(Arrays.asList(myScopes), ",") + ")";
  }

  @Override
  public @NotNull GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (scope instanceof UnionScope) {
      GlobalSearchScope[] newScopes = ArrayUtil.mergeArrays(myScopes, ((UnionScope)scope).myScopes);
      return create(newScopes);
    }
    return super.uniteWith(scope);
  }
}
