// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a module that is implicitly imported.
 */
@ApiStatus.Experimental
public final class ImplicitlyImportedModule implements ImplicitlyImportedElement {

  private final @NotNull String myModuleName;
  private final @NotNull PsiImportModuleStatement myModuleStatement;

  private ImplicitlyImportedModule(@NotNull Project project, @NotNull String moduleName) {
    myModuleName = moduleName;
    myModuleStatement = createImportStatementInner(project, moduleName);
  }

  public @NotNull String getModuleName() {
    return myModuleName;
  }

  @Override
  public @NotNull PsiImportStatementBase createImportStatement() {
    return myModuleStatement;
  }

  private static @NotNull PsiImportModuleStatement createImportStatementInner(@NotNull Project project, @NotNull String moduleName) {
    if (PsiJavaModule.JAVA_BASE.equals(moduleName)) {
      return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
        PsiElementFactory factory = PsiElementFactory.getInstance(project);
        return CachedValueProvider.Result.create(factory.createImportModuleStatementFromText(PsiJavaModule.JAVA_BASE),
                                                 ProjectRootModificationTracker.getInstance(project));
      });
    }
    PsiElementFactory factory = PsiElementFactory.getInstance(project);
    return factory.createImportModuleStatementFromText(moduleName);
  }

  public static @NotNull ImplicitlyImportedModule create(@NotNull Project project, @NotNull String moduleName) {
    return new ImplicitlyImportedModule(project, moduleName);
  }
}
