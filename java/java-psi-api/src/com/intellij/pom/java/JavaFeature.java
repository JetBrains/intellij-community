// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public enum JavaFeature {

  MULTI_CATCH(LanguageLevel.JDK_1_7),
  STREAMS(LanguageLevel.JDK_1_8);

  private final LanguageLevel myMinLevel;

  JavaFeature(LanguageLevel minLevel) {
    myMinLevel = minLevel;
  }

  public static boolean isFeatureSupported(JavaFeature feature, @NotNull PsiFile file) {

    LanguageFeatureProvider[] extensions = LanguageFeatureProvider.EXTENSION_POINT_NAME.getExtensions();
    for (LanguageFeatureProvider extension : extensions) {
      ThreeState threeState = extension.isFeatureSupported(feature, file);
      if (threeState != ThreeState.UNSURE)
        return threeState.toBoolean();
    }
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(file);
    return languageLevel.isAtLeast(feature.myMinLevel);
  }
}
