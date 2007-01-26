/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

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

  boolean createReferences(@NotNull PsiElement psiElement, final @NotNull List<PsiReference> references, final boolean soft);

  @Nullable
  PathReference getPathReference(@NotNull String path, @NotNull final PsiElement element);
}
