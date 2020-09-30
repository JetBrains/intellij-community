// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.meta.PsiPresentableMetaData;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public final class HighlightUsagesDescriptionLocation extends ElementDescriptionLocation {

  private static final ElementDescriptionProvider ourDefaultProvider = (element, location) -> {
    if (element instanceof PsiPresentableMetaData) {
      return ((PsiPresentableMetaData)element).getTypeName();
    }
    return null;
  };

  private HighlightUsagesDescriptionLocation() {
  }

  @NotNull
  @Override
  public ElementDescriptionProvider getDefaultProvider() {
    return ourDefaultProvider;
  }

  public static final HighlightUsagesDescriptionLocation INSTANCE = new HighlightUsagesDescriptionLocation();
}
