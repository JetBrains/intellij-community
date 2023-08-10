// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Register in extension point {@code com.intellij.fileType.fileViewProviderFactory}
 * or {@code com.intellij.lang.fileViewProviderFactory}.
 */
public interface FileViewProviderFactory {
  @NotNull
  FileViewProvider createFileViewProvider(@NotNull VirtualFile file, Language language, @NotNull PsiManager manager, boolean eventSystemEnabled);
}