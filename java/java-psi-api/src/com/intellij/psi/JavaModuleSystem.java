// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows checking accessibility of a given class.
 * <p>
 * A given class is only accessible if all registered extensions consider it accessible
 * 
 * @deprecated there's exactly one module system available, and it's accessible via the {@code JavaModuleGraphHelper} service. Use
 * {@code isAccessible} methods from that service
 */
@Deprecated
public interface JavaModuleSystem {
  ExtensionPointName<JavaModuleSystem> EP_NAME = new ExtensionPointName<>("com.intellij.javaModuleSystem");

  /**
   * Checks accessibility of the class
   *
   * @param target class which accessibility should be determined
   * @param place place where accessibility of target is required
   */
  boolean isAccessible(@NotNull PsiClass target, @NotNull PsiElement place);

  /**
   * Checks accessibility of element in the package
   *
   * @param targetPackageName name of the package which element's accessibility should be determined
   * @param targetFile file in which this element is contained
   * @param place place where accessibility of target is required
   */
  boolean isAccessible(@NotNull String targetPackageName, @Nullable PsiFile targetFile, @NotNull PsiElement place);

  /**
   * Checks accessibility of module in the place
   *
   * @param targetModule the target java module whose accessibility is being checked
   * @param place place where accessibility of target is required
   * @return true if the target module is accessible from the specified location, false otherwise
   */
  boolean isAccessible(@NotNull PsiJavaModule targetModule, @NotNull PsiElement place);
}