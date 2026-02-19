// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.meta.PsiPresentableMetaData;
import org.jetbrains.annotations.NotNull;


public final class HighlightUsagesDescriptionLocation extends ElementDescriptionLocation {

  private static final ElementDescriptionProvider ourDefaultProvider = (element, location) -> {
    if (element instanceof PsiPresentableMetaData) {
      return ((PsiPresentableMetaData)element).getTypeName();
    }
    return null;
  };

  private HighlightUsagesDescriptionLocation() {
  }

  @Override
  public @NotNull ElementDescriptionProvider getDefaultProvider() {
    return ourDefaultProvider;
  }

  public static final HighlightUsagesDescriptionLocation INSTANCE = new HighlightUsagesDescriptionLocation();
}
