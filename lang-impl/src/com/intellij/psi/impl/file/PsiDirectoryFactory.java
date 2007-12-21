package com.intellij.psi.impl.file;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.PsiManagerImpl;

/**
 * @author yole
 */
public abstract class PsiDirectoryFactory {
  public static PsiDirectoryFactory getInstance(Project project) {
    return ServiceManager.getService(project, PsiDirectoryFactory.class);
  }

  public abstract PsiDirectory createDirectory(VirtualFile file);
}
