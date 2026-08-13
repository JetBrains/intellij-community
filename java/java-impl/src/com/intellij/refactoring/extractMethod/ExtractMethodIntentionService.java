// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Access point to the Extract Method refactoring for code which cannot depend on {@code intellij.java.impl.refactorings}.
 * The implementation is registered in that module, so {@link #getInstance()} returns {@code null} when it is not loaded.
 */
public interface ExtractMethodIntentionService {

  static @Nullable ExtractMethodIntentionService getInstance() {
    return ApplicationManager.getApplication().getService(ExtractMethodIntentionService.class);
  }

  boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file);

  void extractMethod(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file);
}
