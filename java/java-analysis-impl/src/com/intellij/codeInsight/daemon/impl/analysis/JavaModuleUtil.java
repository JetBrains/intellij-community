// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaModuleSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public final class JavaModuleUtil {
  private JavaModuleUtil() { }

  /**
   * Determines if a specified module is readable from a given context
   *
   * @param place            current module/position
   * @param targetModuleFile file from the target module
   * @return {@code true} if the target module is readable from the place; {@code false} otherwise.
   */
  public static boolean isModuleReadable(@NotNull PsiElement place,
                                         @NotNull VirtualFile targetModuleFile) {
    PsiJavaModule targetModule = JavaModuleGraphUtil.findDescriptorByFile(targetModuleFile, place.getProject());
    if (targetModule == null) return true;
    return isModuleReadable(place, targetModule);
  }

  /**
   * Determines if the specified modules are readable from a given context.
   *
   * @param place        the current position or element from where readability is being checked
   * @param targetModule the target module to check readability against
   * @return {@code true} if any of the target modules are readable from the current context; {@code false} otherwise
   */
  public static boolean isModuleReadable(@NotNull PsiElement place,
                                         @NotNull PsiJavaModule targetModule) {
    return ContainerUtil.and(JavaModuleSystem.EP_NAME.getExtensionList(), sys -> sys.isAccessible(targetModule, place));
  }
}
