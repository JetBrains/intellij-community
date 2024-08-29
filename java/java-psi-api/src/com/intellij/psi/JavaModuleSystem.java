// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  String ADD_EXPORTS_OPTION = "--add-exports";
  String ADD_OPENS_OPTION = "--add-opens";
  String ADD_MODULES_OPTION = "--add-modules";
  String ADD_READS_OPTION = "--add-reads";
  String PATCH_MODULE_OPTION = "--patch-module";
  String LIST_MODULES_OPTION = "--list-modules";

  String ALL_UNNAMED = "ALL-UNNAMED";
  String ALL_SYSTEM = "ALL-SYSTEM";
  String ALL_MODULE_PATH = "ALL-MODULE-PATH";

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

  /**
   * Checks accessibility of module in the place
   *
   * @param targetModule the target java module whose accessibility is being checked
   * @param place place where accessibility of target is required
   * @return true if the target module is accessible from the specified location, false otherwise
   */
  boolean isAccessible(@NotNull PsiJavaModule targetModule, @NotNull PsiElement place);
}