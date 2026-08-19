// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Access point to Java refactorings for code which cannot depend on {@code intellij.java.impl.refactorings}.
 * The implementation is registered in that module, so {@link #getInstance()} returns {@code null} when it is not loaded.
 */
public interface JavaRefactoringService {

  static @Nullable JavaRefactoringService getInstance() {
    return ApplicationManager.getApplication().getService(JavaRefactoringService.class);
  }

  /**
   * Generates a preview of introducing a local variable of the given {@code type} without an initializer
   * at the caret position of {@code editor}.
   */
  IntentionPreviewInfo generateIntroduceEmptyVariablePreview(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiType type);

  /**
   * Introduces a local variable of the given {@code type} without an initializer at the caret position of {@code editor}.
   */
  void introduceEmptyVariable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiType type);

  /**
   * @return {@code true} if the Extract Method refactoring can be performed on the current selection in {@code editor}
   */
  boolean canExtractMethod(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file);

  /**
   * Performs the Extract Method refactoring on the current selection in {@code editor}.
   */
  void extractMethod(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file);

  /**
   * Shows the Replace Constructor With Builder dialog for the given {@code constructors}.
   */
  void replaceConstructorWithBuilder(@NotNull Project project, PsiMethod @NotNull [] constructors);

  /**
   * Searches the containing class of {@code method} for code fragments duplicating its body and offers to replace them
   * with calls to {@code method}. Duplicates located inside {@code method} itself are ignored.
   */
  void replaceMethodDuplicates(@NotNull PsiMethod method);
}
