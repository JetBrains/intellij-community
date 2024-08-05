// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class JavaModuleGraphHelper {
  @Contract("null->null")
  public abstract @Nullable PsiJavaModule findDescriptorByElement(@Nullable PsiElement element);

  public abstract @NotNull Set<PsiJavaModule> getAllTransitiveDependencies(@NotNull PsiJavaModule psiJavaModule);

  public static JavaModuleGraphHelper getInstance() {
    return ApplicationManager.getApplication().getService(JavaModuleGraphHelper.class);
  }
}