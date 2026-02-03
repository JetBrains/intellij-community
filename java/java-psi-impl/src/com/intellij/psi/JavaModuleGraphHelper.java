// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Provides utilities for operations related to Java modules within the PSI tree.
 */
public abstract class JavaModuleGraphHelper {
  public static JavaModuleGraphHelper getInstance() {
    return ApplicationManager.getApplication().getService(JavaModuleGraphHelper.class);
  }

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

  /**
   * Checks accessibility of the class
   *
   * @param target class which accessibility should be determined
   * @param place place where accessibility of target is required
   */
  public boolean isAccessible(@NotNull PsiClass target, @NotNull PsiElement place) {
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
  public abstract boolean isAccessible(@NotNull String targetPackageName, @Nullable PsiFile targetFile, @NotNull PsiElement place);

  /**
   * Checks accessibility of module in the place
   *
   * @param targetModule the target java module whose accessibility is being checked
   * @param place place where accessibility of target is required
   * @return true if the target module is accessible from the specified location, false otherwise
   */
  public abstract boolean isAccessible(@NotNull PsiJavaModule targetModule, @NotNull PsiElement place);
}