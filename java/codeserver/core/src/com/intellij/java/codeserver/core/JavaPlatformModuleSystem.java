// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.psi.JavaModuleGraphHelper;
import com.intellij.psi.JavaModuleSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Checks package accessibility according to JLS 7 "Packages and Modules".
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se9/html/jls-7.html">JLS 7 "Packages and Modules"</a>
 * @see <a href="http://openjdk.org/jeps/261">JEP 261: Module System</a>
 */
@ApiStatus.Internal
final class JavaPlatformModuleSystem implements JavaModuleSystem {
  @Override
  public boolean isAccessible(@NotNull String targetPackageName, PsiFile targetFile, @NotNull PsiElement place) {
    return JavaModuleGraphHelper.getInstance().isAccessible(targetPackageName, targetFile, place);
  }

  @Override
  public boolean isAccessible(@NotNull PsiJavaModule targetModule, @NotNull PsiElement place) {
    return JavaModuleGraphHelper.getInstance().isAccessible(targetModule, place);
  }

  @Override
  public boolean isAccessible(@NotNull PsiClass target, @NotNull PsiElement place) {
    return JavaModuleGraphHelper.getInstance().isAccessible(target, place);
  }
}