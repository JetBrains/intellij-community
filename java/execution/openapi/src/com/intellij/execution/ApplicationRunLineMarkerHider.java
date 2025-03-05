// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An extension point to validate whether the application run line marker should be displayed.
 */
public interface ApplicationRunLineMarkerHider extends PossiblyDumbAware {

  ExtensionPointName<ApplicationRunLineMarkerHider> EP_NAME =
    ExtensionPointName.create("com.intellij.execution.applicationRunLineMarkerHider");

  static boolean shouldHideRunLineMarker(final @NotNull PsiElement element) {
    List<ApplicationRunLineMarkerHider> extensionList = EP_NAME.getExtensionList();
    List<ApplicationRunLineMarkerHider> filtered = DumbService.getInstance(element.getProject()).filterByDumbAwareness(extensionList);
    if (extensionList.size() != filtered.size()) return true;
    for (ApplicationRunLineMarkerHider extension : extensionList) {
      if (!extension.runLineMarkerAvailable(element)) {
        return true;
      }
    }
    return false;
  }

  @Contract(pure=true)
  boolean runLineMarkerAvailable(@NotNull PsiElement element);
}
