// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.favoritesTreeView;

import com.intellij.codeInspection.reference.RefMethodImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiMethodFavoriteNodeProvider extends FavoriteNodeProvider implements AbstractUrlFavoriteConverter {

  @Override
  public @NotNull String getFavoriteTypeId() {
    return "method";
  }

  @Override
  public @Nullable Object createBookmarkContext(@NotNull Project project, @NotNull String url, @Nullable String moduleName) {
    return RefMethodImpl.findPsiMethod(PsiManager.getInstance(project), url);
  }
}