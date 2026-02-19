// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.paths;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 * @see PathReferenceManager#PATH_REFERENCE_PROVIDER_EP
 */
public interface PathReferenceProvider {

  boolean createReferences(@NotNull PsiElement psiElement, @NotNull List<PsiReference> references, final boolean soft);

  @Nullable
  PathReference getPathReference(@NotNull String path, final @NotNull PsiElement element);
}
