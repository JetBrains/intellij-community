// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public final class ElementDescriptionUtil {
  private ElementDescriptionUtil() { }

  public static @NotNull @NlsSafe String getElementDescription(@NotNull PsiElement element,
                                                               @NotNull ElementDescriptionLocation location) {
    for (ElementDescriptionProvider provider : ElementDescriptionProvider.EP_NAME.getExtensionList()) {
      String result = provider.getElementDescription(element, location);
      if (result != null) {
        return result;
      }
    }

    ElementDescriptionProvider defaultProvider = location.getDefaultProvider();
    if (defaultProvider != null) {
      String result = defaultProvider.getElementDescription(element, location);
      if (result != null) {
        return result;
      }
    }

    return element.toString();
  }
}