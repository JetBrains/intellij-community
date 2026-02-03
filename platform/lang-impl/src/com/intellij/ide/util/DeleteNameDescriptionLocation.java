// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;


public final class DeleteNameDescriptionLocation extends ElementDescriptionLocation {
  private DeleteNameDescriptionLocation() {
  }

  public static final DeleteNameDescriptionLocation INSTANCE = new DeleteNameDescriptionLocation();
  private static final ElementDescriptionProvider ourDefaultProvider = new DefaultProvider();

  @Override
  public @NotNull ElementDescriptionProvider getDefaultProvider() {
    return ourDefaultProvider;
  }

  private static final class DefaultProvider implements ElementDescriptionProvider {
    @Override
    public String getElementDescription(final @NotNull PsiElement element, final @NotNull ElementDescriptionLocation location) {
      if (location instanceof DeleteNameDescriptionLocation) {
        if (element instanceof PsiNamedElement) {
          return ((PsiNamedElement)element).getName();
        }
      }
      return null;
    }
  }
}
