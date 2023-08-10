// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allow adjusting navigation element of a compiled file. Can be used to go to a source files instead.
 */
public interface ClsCustomNavigationPolicy {
  ExtensionPointName<ClsCustomNavigationPolicy> EP_NAME = ExtensionPointName.create("com.intellij.psi.clsCustomNavigationPolicy");

  /**
   * Searches for a navigation element for a given file
   * @param clsFile file for which navigation element should be found
   * @return navigation element to use instead
   */
  @Nullable
  default PsiElement getNavigationElement(@NotNull ClsFileImpl clsFile) { return null; }

  /**
   * Searches for a navigation element for a given class
   * @param clsClass class for which navigation element should be found
   * @return navigation element to use instead
   */
  @Nullable
  default PsiElement getNavigationElement(@NotNull ClsClassImpl clsClass) { return null; }

  /**
   * Searches for a navigation element for a given method
   * @param clsMethod method for which navigation element should be found
   * @return navigation element to use instead
   */
  @Nullable
  default PsiElement getNavigationElement(@NotNull ClsMethodImpl clsMethod) { return null; }

  /**
   * Searches for a navigation element for a given file
   * @param clsField field for which navigation element should be found
   * @return navigation element to use instead
   */
  @Nullable
  default PsiElement getNavigationElement(@NotNull ClsFieldImpl clsField) { return null; }
}