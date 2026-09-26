// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiPackageFavoriteNodeProvider extends FavoriteNodeProvider implements AbstractUrlFavoriteConverter {

  @Override
  public @NotNull String getFavoriteTypeId() {
    return "package";
  }

  @Override
  public @Nullable Object createBookmarkContext(@NotNull Project project, @NotNull String url, @Nullable String moduleName) {
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    // module can be null if 'show module' turned off
    final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(url);
    if (aPackage == null) return null;
    return new PackageElement(module, aPackage, false);
  }
}