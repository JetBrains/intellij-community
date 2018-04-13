// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public interface LanguageFeatureProvider {

  ExtensionPointName<LanguageFeatureProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.languageFeatureProvider");

  @NotNull
  ThreeState isFeatureSupported(JavaFeature feature, @NotNull PsiFile file);
}
