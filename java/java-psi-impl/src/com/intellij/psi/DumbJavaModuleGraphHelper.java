// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * An implementation of {@link JavaModuleGraphHelper} which does not support the Java platform module system:
 * no modules are defined, and everything is accessible.
 */
@ApiStatus.Internal
public final class DumbJavaModuleGraphHelper extends JavaModuleGraphHelper {
  @Override
  public @Nullable PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
    return null;
  }

  @Override
  public @NotNull Set<PsiJavaModule> getAllTransitiveDependencies(@NotNull PsiJavaModule psiJavaModule) {
    return Collections.emptySet();
  }

  @Override
  public boolean isAccessible(@NotNull String targetPackageName, @Nullable PsiFile targetFile, @NotNull PsiElement place) {
    return true;
  }

  @Override
  public boolean isAccessible(@NotNull PsiJavaModule targetModule, @NotNull PsiElement place) {
    return true;
  }
}
