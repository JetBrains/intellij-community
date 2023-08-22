// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
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
   * In order to customize notification popup after reformat code action just return it from {@link #processFile} with proper information,
   * by default "imports optimized" is shown.
   */
  interface CollectingInfoRunnable extends Runnable {
    @Nullable @NlsContexts.HintText String getUserNotificationInfo();
  }
}
