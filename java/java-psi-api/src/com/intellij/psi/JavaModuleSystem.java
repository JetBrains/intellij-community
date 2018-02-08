// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface JavaModuleSystem {
  ExtensionPointName<JavaModuleSystem> EP_NAME = new ExtensionPointName<>("com.intellij.javaModuleSystem");

  @NotNull String getName();

  default boolean isAccessible(@NotNull PsiClass target, @NotNull PsiElement place) {
    PsiFile file = target.getContainingFile();
    return !(file instanceof PsiClassOwner) || isAccessible(((PsiClassOwner)file).getPackageName(), (PsiClassOwner)file, place);
  }

  boolean isAccessible(@NotNull String targetPackageName, @Nullable PsiClassOwner targetFile, @NotNull PsiElement place);
}