// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used in exceptional cases when PSI is required to find out correct indent options for the current file.
 */
public abstract class PsiBasedFileIndentOptionsProvider extends FileIndentOptionsProvider {

  /**
   * Retrieves indent options for PSI file from the given base settings.
   * @param settings Code style settings for which indent options are calculated.
   * @param file The file to retrieve options for.
   * @return Indent options or {@code null} if the provider is not applicable.
   */
  public abstract @Nullable CommonCodeStyleSettings.IndentOptions getIndentOptionsByPsiFile(@NotNull CodeStyleSettings settings,
                                                                                            @NotNull PsiFile file);

  @Override
  public final @Nullable CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull Project project,
                                                                                @NotNull CodeStyleSettings settings,
                                                                                @NotNull VirtualFile file) {
    if (!file.isValid()) return null;
    //avoid calling getDocument, because it causes decompilation, which is unnecessary
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile != null) {
      return getIndentOptionsByPsiFile(settings, psiFile);
    }
    return null;
  }
}
