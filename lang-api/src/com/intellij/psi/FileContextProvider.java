package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class FileContextProvider {

  private final static ExtensionPointName<FileContextProvider> EP_NAME = new ExtensionPointName<FileContextProvider>("com.intellij.fileContextProvider");

  @Nullable
  public static FileContextProvider getProvider(final @NotNull PsiFile file) {
    for (FileContextProvider provider: Extensions.getExtensions(EP_NAME, file.getProject())) {
      if (provider.isAvailable(file)) {
        return provider;
      }
    }
    return null;
  }

  protected abstract boolean isAvailable(final PsiFile file);

  @Nullable
  public abstract PsiFileSystemItem getContextFolder(final PsiFile file);

  @Nullable
  public abstract PsiFile getContextFile(final PsiFile file);
}
