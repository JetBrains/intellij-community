// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public abstract class FileContextProvider {

  public static final ExtensionPointName<FileContextProvider> EP_NAME = new ExtensionPointName<>("com.intellij.fileContextProvider");

  public static @Nullable FileContextProvider getProvider(final @NotNull PsiFile file) {
    for (FileContextProvider provider : EP_NAME.getExtensions(file.getProject())) {
      if (provider.isAvailable(file)) {
        return provider;
      }
    }
    return null;
  }

  protected abstract boolean isAvailable(final PsiFile file);

  public abstract @NotNull Collection<PsiFileSystemItem> getContextFolders(final PsiFile file);

  public abstract @Nullable PsiFile getContextFile(final PsiFile file);
}
