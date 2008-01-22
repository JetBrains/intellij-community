package com.intellij.psi.impl.file;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.PsiManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PsiDirectoryFactoryImpl extends PsiDirectoryFactory {
  private PsiManagerImpl myManager;

  public PsiDirectoryFactoryImpl(final PsiManagerImpl manager) {
    myManager = manager;
  }
  public PsiDirectory createDirectory(final VirtualFile file) {
    return new PsiDirectoryImpl(myManager, file);
  }

  @NotNull
  public String getQualifiedName(@NotNull final PsiDirectory directory) {
    return directory.getVirtualFile().getPresentableUrl();
  }

  @Nullable
  public String getComment(@NotNull final PsiDirectory psiDirectory, final boolean forceLocation) {
    final VirtualFile directory = psiDirectory.getVirtualFile();
    final VirtualFile contentRootForFile = ProjectRootManager.getInstance(myManager.getProject())
      .getFileIndex().getContentRootForFile(directory);
    if (Comparing.equal(contentRootForFile, psiDirectory)) {
      return directory.getPresentableUrl();
    }
    return null;
  }

  public PsiManagerImpl getManager() {
    return myManager;
  }
}
