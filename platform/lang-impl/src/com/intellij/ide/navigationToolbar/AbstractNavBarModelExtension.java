// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author gregsh
 */
public abstract class AbstractNavBarModelExtension implements NavBarModelExtension {
  @Override
  public abstract @Nullable String getPresentableText(Object object);

  @Override
  public @Nullable PsiElement adjustElement(@NotNull PsiElement psiElement) {
    return psiElement;
  }

  @Override
  public @Nullable PsiElement getParent(PsiElement psiElement) {
    return null;
  }

  @Override
  public @NotNull Collection<VirtualFile> additionalRoots(Project project) {
    return Collections.emptyList();
  }
}
