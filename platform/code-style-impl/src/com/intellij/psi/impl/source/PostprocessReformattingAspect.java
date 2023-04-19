// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public abstract class PostprocessReformattingAspect implements PomModelAspect {
  public abstract void disablePostprocessFormattingInside(@NotNull Runnable runnable);

  public abstract <T> T disablePostprocessFormattingInside(@NotNull Computable<T> computable);

  public abstract void postponeFormattingInside(@NotNull Runnable runnable);

  public abstract <T> T postponeFormattingInside(@NotNull Computable<T> computable);

  public abstract void forcePostprocessFormatInside(@NotNull PsiFile psiFile, @NotNull Runnable runnable);

  @Override
  public abstract void update(@NotNull PomModelEvent event);

  public abstract void doPostponedFormatting();

  public abstract void doPostponedFormatting(@NotNull FileViewProvider viewProvider);

  public abstract boolean isViewProviderLocked(@NotNull FileViewProvider fileViewProvider);

  public abstract boolean isDocumentLocked(@NotNull Document document);

  /**
   * Checks that view provider doesn't contain any PSI modifications which will be used in postponed formatting and may conflict with
   * changes made to the document.
   *
   * @param viewProvider The view provider to validate.
   * @throws RuntimeException If the assertion fails.
   */
  public abstract void assertDocumentChangeIsAllowed(@NotNull FileViewProvider viewProvider);

  public abstract boolean isDisabled();

  @TestOnly
  public abstract void clear();

  public static PostprocessReformattingAspect getInstance(@NotNull Project project) {
    return PomManager.getModel(project).getModelAspect(PostprocessReformattingAspect.class);
  }

  public static void assertDocumentChangeIsAllowed(@NotNull PsiFile file) {
    PostprocessReformattingAspect reformattingAspect = getInstance(file.getProject());
    reformattingAspect.assertDocumentChangeIsAllowed(file.getViewProvider());
  }
}
