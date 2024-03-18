// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Hides {@link NonCodeAnnotationsLineMarkerProvider} markers depending on PSI elements.
 * Used to decrease the number of line markers on popular methods, e.g., URL mappings or tests.
 */
public interface NonCodeAnnotationsMarkerSuppressor {
  ExtensionPointName<NonCodeAnnotationsMarkerSuppressor> EP_NAME =
    ExtensionPointName.create("com.intellij.lang.jvm.annotations.marker.suppressor");

  boolean isLineMarkerSuppressed(@NotNull PsiElement element);
}
