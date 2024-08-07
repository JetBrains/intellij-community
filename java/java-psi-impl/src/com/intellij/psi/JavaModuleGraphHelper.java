// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Provides utilities for operations related to Java modules within the PSI tree.
 */
public abstract class JavaModuleGraphHelper {
  /**
   * Retrieves the {@link PsiJavaModule} associated with the specified {@link PsiElement}.
   *
   * @param element the PSI element for which to locate the module descriptor, may be {@code null}.
   * @return the corresponding {@link PsiJavaModule} or {@code null} if the element is {@code null}
   * or no module is found.
   */
  @Contract("null->null")
  public abstract @Nullable PsiJavaModule findDescriptorByElement(@Nullable PsiElement element);

  /**
   * Retrieves all dependencies for the given Java module, including direct and transitive dependencies.
   *
   * @param psiJavaModule the {@link PsiJavaModule} for which transitive dependencies are to be collected
   * @return a set of transitive dependencies of the specified {@link PsiJavaModule}
   */
  public abstract @NotNull Set<PsiJavaModule> getAllTransitiveDependencies(@NotNull PsiJavaModule psiJavaModule);

  public static JavaModuleGraphHelper getInstance() {
    return ApplicationManager.getApplication().getService(JavaModuleGraphHelper.class);
  }
}