// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.injected.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface InjectionEditService {
  /**
   * Synchronizes the content of injectedFile with the content of copyDocument.
   *
   * @param injectedFile file to apply changes to
   * @param copyDocument unescaped non-physical copy of the original injected file,
   *                     where additional changes will be applied.
   * @return disposable to dispose when synchronization is not needed anymore
   */
  @NotNull Disposable synchronizeWithFragment(@NotNull PsiFile injectedFile, @NotNull Document copyDocument);
}
