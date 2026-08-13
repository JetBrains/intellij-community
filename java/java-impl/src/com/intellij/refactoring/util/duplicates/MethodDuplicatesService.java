// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.duplicates;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Access point to the method duplicates search for code which cannot depend on {@code intellij.java.impl.refactorings}.
 * The implementation is registered in that module, so {@link #getInstance()} returns {@code null} when it is not loaded.
 */
public interface MethodDuplicatesService {

  static @Nullable MethodDuplicatesService getInstance() {
    return ApplicationManager.getApplication().getService(MethodDuplicatesService.class);
  }

  /**
   * Searches the containing class of {@code method} for code fragments duplicating its body and offers to replace them
   * with calls to {@code method}. Duplicates located inside {@code method} itself are ignored.
   */
  void replaceMethodDuplicates(@NotNull PsiMethod method);
}
