/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang;

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
   * Call to this method is made before the <code>processFile()</code> call to ensure implementation can process the file given
   *
   * @param file file to check
   * @return <code>true</code> if implementation can handle the file
   */
  boolean supports(PsiFile file);

  /**
   * Implementers of the method are expected to perform all necessary calculations synchronously and return a <code>Runnable</code>,
   * which performs modifications based on preprocessing results.
   * processFile() is guaranteed to run with {@link com.intellij.openapi.application.Application#runReadAction(Runnable)} privileges and
   * the Runnable returned is guaranteed to run with {@link com.intellij.openapi.application.Application#runWriteAction(Runnable)} privileges.
   *
   * One can theoretically delay all the calculation until Runnable is called but this code will be executed in Swing thread thus
   * lengthy calculations may block user interface for some significant time.
   *
   * @param file to optimize an imports in. It's guaranteed to have a language this <code>ImportOptimizer</code> have been
   * issued from.
   * @return a <code>java.lang.Runnable</code> object, which being called will replace original file imports with optimized version.
   */
  @NotNull
  Runnable processFile(PsiFile file);

  /**
   * In order to customize notification popup after reformat code action just return it from {@link #processFile} with proper information,
   * by default "imports optimized" is shown.
   */
  interface CollectingInfoRunnable extends Runnable {
    @Nullable String getUserNotificationInfo();
  }
}
