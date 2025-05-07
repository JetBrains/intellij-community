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
}
