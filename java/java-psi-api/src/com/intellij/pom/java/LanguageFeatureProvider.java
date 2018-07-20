// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * This can be used to modify Java language features availability depending on context (e.g. due to specific runtime implementation).
 * @see JavaFeature
 */
public interface LanguageFeatureProvider {

  ExtensionPointName<LanguageFeatureProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.java.languageFeatureProvider");

  /**
   * @return {@link ThreeState#YES} or {@link ThreeState#NO} to alternate default ({@link LanguageLevel}-based) availability,
   * or {@link ThreeState#UNSURE} otherwise.
   */
  @NotNull
  ThreeState isFeatureSupported(@NotNull JavaFeature feature, @NotNull PsiFile file);
}
