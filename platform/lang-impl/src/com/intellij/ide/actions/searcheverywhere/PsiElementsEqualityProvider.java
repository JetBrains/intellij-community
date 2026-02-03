// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class PsiElementsEqualityProvider extends AbstractEqualityProvider {

  @Override
  public boolean areEqual(@NotNull SearchEverywhereFoundElementInfo newItemInfo, @NotNull SearchEverywhereFoundElementInfo alreadyFoundItemInfo) {
    PsiElement newElementPsi = toPsi(newItemInfo.getElement());
    PsiElement alreadyFoundPsi = toPsi(alreadyFoundItemInfo.getElement());

    return newElementPsi != null && alreadyFoundPsi != null
           && Objects.equals(newElementPsi, alreadyFoundPsi);
  }

  public static @Nullable PsiElement toPsi(Object o) {
    return PSIPresentationBgRendererWrapper.toPsi(o);
  }
}
