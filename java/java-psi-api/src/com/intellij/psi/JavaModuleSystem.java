// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface JavaModuleSystem {
  ExtensionPointName<JavaModuleSystem> EP_NAME = new ExtensionPointName<>("com.intellij.javaModuleSystem");

  @NotNull String getName();

  default boolean isAccessible(@NotNull PsiClass target, @NotNull PsiElement place) {
    PsiFile targetFile = target.getContainingFile();
    PsiUtilCore.ensureValid(targetFile);

    String packageName = PsiUtil.getPackageName(target);
    return packageName == null || isAccessible(packageName, targetFile, place);
  }

  boolean isAccessible(@NotNull String targetPackageName, @Nullable PsiFile targetFile, @NotNull PsiElement place);
}