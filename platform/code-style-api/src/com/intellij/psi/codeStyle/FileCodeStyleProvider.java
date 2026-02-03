// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to override current project code style settings with file-specific ones.
 */
public interface FileCodeStyleProvider {
  ExtensionPointName<FileCodeStyleProvider> EP_NAME = ExtensionPointName.create("com.intellij.fileCodeStyleProvider");

  /**
   * @param file The PSI file for which alternative code style settings must be used.
   * @return The code style settings to use or {@code null} if the provider is not applicable to the given file.
   */
  @Nullable
  CodeStyleSettings getSettings(@NotNull PsiFile file);
}
