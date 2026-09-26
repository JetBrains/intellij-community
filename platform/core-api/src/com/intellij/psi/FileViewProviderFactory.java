// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Register in extension point {@code com.intellij.fileType.fileViewProviderFactory}
 * or {@code com.intellij.lang.fileViewProviderFactory}.
 */
public interface FileViewProviderFactory {
  /**
   * @param file               the given file
   * @param language           the language of the file. Can be {@code null} if the factory is registered for a file type
   *                           without a dedicated language.
   * @param manager            PsiManager to use
   * @param eventSystemEnabled see doc of {@link FileViewProvider#supportsSendingPsiEvents}
   * @return a new file view provider for the given file
   */
  @Contract(pure = true)
  @NotNull FileViewProvider createFileViewProvider(@NotNull VirtualFile file,
                                                   Language language,
                                                   @NotNull PsiManager manager,
                                                   boolean eventSystemEnabled);
}