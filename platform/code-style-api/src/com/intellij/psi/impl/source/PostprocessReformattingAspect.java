// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.openapi.Disposable;
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

/**
 * Allows to control automatic reformatting
 */
public abstract class PostprocessReformattingAspect implements PomModelAspect {
  public abstract void disablePostprocessFormattingInside(@NotNull Runnable runnable);

  public abstract <T> T disablePostprocessFormattingInside(@NotNull Computable<T> computable);

  /**
   * Runs the runnable with automatic formatting turned off, then apply it at once 
   * @param runnable runnable to run
   */
  public abstract void postponeFormattingInside(@NotNull Runnable runnable);

  /**
   * Runs the computable with automatic formatting turned off, then apply it at once and returns the computable result
   * @param <T> type of computable result
   * @param computable computable to run
   * @return result of computable
   */
  public abstract <T> T postponeFormattingInside(@NotNull Computable<T> computable);

  public abstract void forcePostprocessFormatInside(@NotNull PsiFile psiFile, @NotNull Runnable runnable);
  
  public abstract void forcePostprocessFormat(@NotNull PsiFile psiFile, @NotNull Disposable disposable);

  @Override
  public abstract void update(@NotNull PomModelEvent event);

  /**
   * Perform all the postponed formatting. Mostly useful for tests.
   */
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

  /**
   * @param project project
   * @return the {@link PostprocessReformattingAspect} instance
   */
  public static PostprocessReformattingAspect getInstance(@NotNull Project project) {
    return PomManager.getModel(project).getModelAspect(PostprocessReformattingAspect.class);
  }
}
