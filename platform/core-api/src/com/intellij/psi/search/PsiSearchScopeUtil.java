// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.lang.LanguageMatcher;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiSearchScopeUtil {

  public static final Key<SearchScope> USE_SCOPE_KEY = Key.create("search.use.scope");

  @Nullable
  public static SearchScope union(@Nullable SearchScope a, @Nullable SearchScope b) {
    return a == null ? b : b == null ? a : a.union(b);
  }

  public static boolean isInScope(@NotNull SearchScope scope, @NotNull PsiElement element) {
    if (scope instanceof LocalSearchScope) {
      LocalSearchScope local = (LocalSearchScope)scope;
      return isInScope(local, element);
    }
    else {
      GlobalSearchScope globalScope = (GlobalSearchScope)scope;
      return isInScope(globalScope, element);
    }
  }

  public static boolean isInScope(@NotNull GlobalSearchScope globalScope, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return true;
    }
    while (file != null) {
      if (globalScope.contains(BackedVirtualFile.getOriginFileIfBacked(file.getOriginalFile().getViewProvider().getVirtualFile()))) {
        return true;
      }
      PsiElement context = file.getContext();
      file = context == null ? null : context.getContainingFile();
    }
    return false;
  }

  public static boolean isInScope(@NotNull LocalSearchScope local, @NotNull PsiElement element) {
    PsiElement[] scopeElements = local.getScope();
    for (final PsiElement scopeElement : scopeElements) {
      if (PsiTreeUtil.isAncestor(scopeElement, element, false)) return true;
    }
    return false;
  }

  @NotNull
  @Contract(pure=true)
  public static SearchScope restrictScopeTo(@NotNull SearchScope originalScope, FileType @NotNull ... fileTypes) {
    if (originalScope instanceof GlobalSearchScope) {
      return GlobalSearchScope.getScopeRestrictedByFileTypes(
        (GlobalSearchScope)originalScope,
        fileTypes
      );
    }
    return LocalSearchScope.getScopeRestrictedByFileTypes(
      (LocalSearchScope)originalScope,
      fileTypes
    );
  }

  @ApiStatus.Experimental
  @Contract(pure = true)
  @NotNull
  public static SearchScope restrictScopeToFileLanguage(@NotNull Project project,
                                                        @NotNull SearchScope originalScope,
                                                        @NotNull LanguageMatcher matcher) {
    if (originalScope instanceof GlobalSearchScope) {
      return new FileLanguageGlobalScope(project, (GlobalSearchScope)originalScope, matcher);
    }
    else {
      return LocalSearchScope.getScopeRestrictedByFileLanguage((LocalSearchScope)originalScope, matcher);
    }
  }
}
