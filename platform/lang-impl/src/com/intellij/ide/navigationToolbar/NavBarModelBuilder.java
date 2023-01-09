// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Creates a new model for navigation bar by taking a giving element and
 *  traverse path to root adding each element to a model
 *  
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
public abstract class NavBarModelBuilder {

  public static NavBarModelBuilder getInstance() {
    return ApplicationManager.getApplication().getService(NavBarModelBuilder.class);
  }

  public List<Object> createModel(@NotNull PsiElement psiElement,
                                  @NotNull Set<VirtualFile> roots,
                                  @Nullable DataContext dataContext,
                                  @Nullable NavBarModelExtension ownerExtension) {
    final List<Object> model = new ArrayList<>();
    traverseToRoot(psiElement, roots, model, dataContext, ownerExtension);
    return model;
  }

  abstract void traverseToRoot(@NotNull PsiElement psiElement,
                               @NotNull Set<VirtualFile> roots,
                               @NotNull List<Object> model,
                               @Nullable DataContext dataContext,
                               @Nullable NavBarModelExtension ownerExtension);
}

