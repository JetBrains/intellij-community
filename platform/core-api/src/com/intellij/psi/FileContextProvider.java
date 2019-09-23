// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  @Nullable
  public static FileContextProvider getProvider(@NotNull final PsiFile file) {
    for (FileContextProvider provider: EP_NAME.getExtensions(file.getProject())) {
      if (provider.isAvailable(file)) {
        return provider;
      }
    }
    return null;
  }

  protected abstract boolean isAvailable(final PsiFile file);

  @NotNull
  public abstract Collection<PsiFileSystemItem> getContextFolders(final PsiFile file);

  @Nullable
  public abstract PsiFile getContextFile(final PsiFile file);
}
