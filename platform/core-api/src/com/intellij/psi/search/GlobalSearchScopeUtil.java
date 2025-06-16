// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.stream.Collectors;

public final class GlobalSearchScopeUtil {
  public static @NotNull GlobalSearchScope toGlobalSearchScope(@NotNull SearchScope scope, @NotNull Project project) {
    if (scope instanceof GlobalSearchScope) {
      return (GlobalSearchScope)scope;
    }
    return ReadAction.compute(() -> GlobalSearchScope.filesScope(project, getLocalScopeFiles((LocalSearchScope)scope)));
  }

  public static @NotNull @Unmodifiable Set<VirtualFile> getLocalScopeFiles(@NotNull LocalSearchScope scope) {
    return ReadAction.compute(() -> {
      Set<VirtualFile> files = new LinkedHashSet<>();
      for (PsiElement element : scope.getScope()) {
        PsiFile file = element.getContainingFile();
        if (file != null) {
          ContainerUtil.addIfNotNull(files, file.getVirtualFile());
          ContainerUtil.addIfNotNull(files, file.getNavigationElement().getContainingFile().getVirtualFile());
        }
      }
      return files;
    });
  }

  /**
   * Recursively unwraps nested {@link UnionScope}s and returns a list of all contained non-union scopes.
   */
  public static @NotNull List<GlobalSearchScope> flattenUnionScope(@NotNull GlobalSearchScope scope) {
    if (scope instanceof UnionScope) {
      UnionScope unionScope = (UnionScope)scope;
      return Arrays.stream(unionScope.myScopes)
        .flatMap(s -> flattenUnionScope(s).stream()).collect(Collectors.toList());
    }
    return Collections.singletonList(scope);
  }

  /**
   * Whether the given scope is an {@link IntersectionScope}.
   */
  public static boolean isIntersectionScope(@NotNull GlobalSearchScope scope) {
    return scope instanceof IntersectionScope;
  }

  /**
   * Recursively unwraps nested {@link IntersectionScope}s and returns a list of all contained non-intersection scopes.
   */
  public static @NotNull List<GlobalSearchScope> flattenIntersectionScope(@NotNull GlobalSearchScope scope) {
    if (scope instanceof IntersectionScope) {
      ArrayList<GlobalSearchScope> result = new ArrayList<>();
      flattenIntersectionScopeInto(scope, result);
      return result;
    }
    return Collections.singletonList(scope);
  }

  private static void flattenIntersectionScopeInto(@NotNull GlobalSearchScope scope, @NotNull List<GlobalSearchScope> result) {
    if (scope instanceof IntersectionScope) {
      IntersectionScope intersectionScope = (IntersectionScope)scope;
      flattenIntersectionScopeInto(intersectionScope.myScope1, result);
      flattenIntersectionScopeInto(intersectionScope.myScope2, result);
    } else {
      result.add(scope);
    }
  }

  /**
   * Modifies the scope if necessary to include the file of the given element.
   * This is useful to properly support search in scratch files.
   * 
   * @param scope scope to update
   * @param element element whose file should be included in the scope
   * @return the corrected scope. Returns the original scope if it already includes the file of the element.
   */
  public static GlobalSearchScope includeContainingFile(@NotNull GlobalSearchScope scope, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      VirtualFile vFile = file.getVirtualFile();
      if (vFile != null && !scope.contains(vFile)) {
        return scope.union(GlobalSearchScope.fileScope(file));
      }
    }
    return scope;
  }
}
