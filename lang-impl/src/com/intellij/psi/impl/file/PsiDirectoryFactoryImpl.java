package com.intellij.psi.impl.file;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.PsiManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiDirectoryFactoryImpl extends PsiDirectoryFactory {
  private final PsiManagerImpl myManager;

  public PsiDirectoryFactoryImpl(final PsiManagerImpl manager) {
    myManager = manager;
  }
  public PsiDirectory createDirectory(final VirtualFile file) {
    return new PsiDirectoryImpl(myManager, file);
  }

  @NotNull
  public String getQualifiedName(@NotNull final PsiDirectory directory, final boolean presentable) {
    if (presentable) {
      return directory.getVirtualFile().getPresentableUrl();
    }
    return "";
  }

  @Override
  public boolean isPackage(PsiDirectory directory) {
    return false;
  }
}
