// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows checking accessibility of a given class.
 * <p>
 * A given class is only accessible if all registered extensions consider it accessible
 *
 * @see com.intellij.codeInsight.JavaModuleSystemEx
 */
public interface JavaModuleSystem {
  ExtensionPointName<JavaModuleSystem> EP_NAME = new ExtensionPointName<>("com.intellij.javaModuleSystem");

  /**
   * @return name of the module system which will be reported to user in case of inaccessibility
   */
  @Nls
  @NotNull String getName();

  /**
   * Checks accessibility of the class
   *
   * @param target class which accessibility should be determined
   * @param place place where accessibility of target is required
   */
  default boolean isAccessible(@NotNull PsiClass target, @NotNull PsiElement place) {
    PsiFile targetFile = target.getContainingFile();
    if (targetFile == null) return true;

    PsiUtilCore.ensureValid(targetFile);

    String packageName = PsiUtil.getPackageName(target);
    return packageName == null || isAccessible(packageName, targetFile, place);
  }

  /**
   * Checks accessibility of element in the package
   *
   * @param targetPackageName name of the package which element's accessibility should be determined
   * @param targetFile file in which this element is contained
   * @param place place where accessibility of target is required
   */
  boolean isAccessible(@NotNull String targetPackageName, @Nullable PsiFile targetFile, @NotNull PsiElement place);
}