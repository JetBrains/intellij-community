// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides location-dependent element description.
 */
public interface ElementDescriptionProvider {
  ExtensionPointName<ElementDescriptionProvider> EP_NAME = ExtensionPointName.create("com.intellij.elementDescriptionProvider");
  
  @Nullable
  @NlsSafe String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location);
}
