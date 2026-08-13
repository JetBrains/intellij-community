// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Access point to the Replace Constructor With Builder refactoring for code which cannot depend on
 * {@code intellij.java.impl.refactorings}. The implementation is registered in that module, so {@link #getInstance()}
 * returns {@code null} when it is not loaded.
 */
public interface ReplaceConstructorWithBuilderService {

  static @Nullable ReplaceConstructorWithBuilderService getInstance() {
    return ApplicationManager.getApplication().getService(ReplaceConstructorWithBuilderService.class);
  }

  void showDialog(@NotNull Project project, PsiMethod @NotNull [] constructors);
}
