// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * {@link com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet} uses the providers if
 * {@link com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet#useIncludingFileAsContext()} is true
 *
 * @see com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper
 */
public abstract class FileContextProvider {
  public static final ExtensionPointName<FileContextProvider> EP_NAME = new ExtensionPointName<>("com.intellij.fileContextProvider");

  public static @Nullable FileContextProvider getProvider(final @NotNull PsiFile hostFile) {
    for (FileContextProvider provider : EP_NAME.getExtensionList(hostFile.getProject())) {
      if (provider.isAvailable(hostFile)) {
        return provider;
      }
    }
    return null;
  }

  protected abstract boolean isAvailable(final PsiFile hostFile);

  public abstract @NotNull @Unmodifiable Collection<PsiFileSystemItem> getContextFolders(final PsiFile hostFile);

  public abstract @Nullable PsiFile getContextFile(final PsiFile hostFile);
}
