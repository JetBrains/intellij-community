// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * An extension point to validate whether the application run line marker should be displayed.
 */
public interface ApplicationRunLineMarkerHider {

  ExtensionPointName<ApplicationRunLineMarkerHider> EP_NAME =
    ExtensionPointName.create("com.intellij.execution.applicationRunLineMarkerHider");

  static boolean shouldHideRunLineMarker(@NotNull final PsiElement element) {
    for (ApplicationRunLineMarkerHider extension : EP_NAME.getExtensionList()) {
      if (!extension.runLineMarkerAvailable(element)) {
        return true;
      }
    }
    return false;
  }

  @Contract(pure=true)
  boolean runLineMarkerAvailable(@NotNull PsiElement element);
}
