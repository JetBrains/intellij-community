// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementers of the interface encapsulate optimize imports process for the language.
 * Should be registered as "com.intellij.lang.importOptimizer" extension.
 * @author max
 * @see LanguageImportStatements
 */
public interface ImportOptimizer {
  /**
   * Call to this method is made before the {@code processFile()} call to ensure implementation can process the file given
   *
   * @param file file to check
   * @return {@code true} if implementation can handle the file
   */
  boolean supports(@NotNull PsiFile file);

  /**
   * Implementers of the method are expected to perform all necessary calculations synchronously and return a {@code Runnable},
   * which performs modifications based on preprocessing results.
   * processFile() is guaranteed to run with {@link com.intellij.openapi.application.Application#runReadAction(Runnable)} privileges and
   * the Runnable returned is guaranteed to run with {@link com.intellij.openapi.application.Application#runWriteAction(Runnable)} privileges.
   *
   * One can theoretically delay all the calculation until Runnable is called but this code will be executed in Swing thread thus
   * lengthy calculations may block user interface for some significant time.
   *
   * @param file to optimize an imports in. It's guaranteed to have a language this {@code ImportOptimizer} have been
   * issued from.
   * @return a {@code java.lang.Runnable} object, which being called will replace original file imports with optimized version.
   */
  @NotNull
  Runnable processFile(@NotNull PsiFile file);

  /**
   * Lets the language plugin describe how the write action that wraps the {@link Runnable} returned by
   * {@link #processFile} (and the auto-import step that follows on EDT) should be executed for the given {@code file}.
   * <p>
   * The default is {@link ActionMode#WRITE_COMMAND_ACTION} — the platform opens a regular
   * {@link com.intellij.openapi.command.WriteCommandAction} around the {@code Runnable}, identical to how
   * {@code AbstractLayoutCodeProcessor} processes reformat/rearrange tasks.
   * <p>
   * Languages whose optimize-imports / auto-import pipeline may perform long operations on EDT (for example,
   * deep resolve during unambiguous auto-import on the fly) should return {@link ActionMode#EDT}.
   * Even if {@link getActionMode} returns {@link ActionMode#EDT}, this {@link Runnable} can be called as with {@link  ActionMode#WRITE_COMMAND_ACTION}
   */
  @ApiStatus.Experimental
  default @NotNull ImportOptimizer.ActionMode getActionMode() {
    return ActionMode.WRITE_COMMAND_ACTION;
  }

  /**
   * Strategy describing how auto-import results should be executed.
   *
   */
  @ApiStatus.Experimental
  enum ActionMode {
    /**
     * Default behaviour: the platform wraps the {@code Runnable} returned by {@link #processFile} into a regular
     * {@link com.intellij.openapi.command.WriteCommandAction}. The {@code Runnable} sees an already-open write
     * action and undo command.
     */
    WRITE_COMMAND_ACTION,

    /**
     * The platform opens only an undo command on EDT around the {@code Runnable} returned by {@link #processFile};
     * it does <em>not</em> enter a write action. The {@code Runnable} itself must enter in a write action.
     */
    EDT
  }

  /**
   * In order to customize notification popup after reformat code action just return it from {@link #processFile} with proper information,
   * by default "imports optimized" is shown.
   */
  interface CollectingInfoRunnable extends Runnable {
    @Nullable @NlsContexts.HintText String getUserNotificationInfo();
  }
}
