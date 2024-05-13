// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface JavaTestFrameworkInDumbMode {
  ExtensionPointName<JavaTestFrameworkInDumbMode> EP = ExtensionPointName.create("com.intellij.testFramework.java.dumbMode");

  /**
   * Checks whether the given PsiElement can be used in dumb mode for the JavaTestFramework.
   * Can be called either in dumb mode or usual mode.
   * Usually, it is used to check language only.
   *
   * @param psiElement the PsiElement to be checked.
   * @return true if the PsiElement is supported, false otherwise.
   */
  boolean supportDumbMode(@NotNull PsiElement psiElement);

  /**
   * Checks whether the given PsiClass can be used in dumb mode for JavaTestFramework.
   * Can be called either in dumb mode or usual mode.
   * Usually, it is used to check language only
   *
   * @param psiElement the PsiElement to be checked.
   * @return true if the PsiElement is supported, false otherwise.
   */
  static boolean isSupported(@NotNull PsiElement psiElement) {
    for (JavaTestFrameworkInDumbMode framework : EP.getExtensionList()) {
      if (framework.supportDumbMode(psiElement)) {
        return true;
      }
    }
    return false;
  }
}
