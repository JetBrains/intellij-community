// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PsiPackageImplUtil {
  private PsiPackageImplUtil() {
  }

  /**
   * @return true if the given directory is located under the source root and corresponds to jvm package, false otherwise
   */
  public static boolean isDirectoryUnderPackage(@Nullable PsiElement element) {
    if (!(element instanceof PsiDirectory directory)) return false;
    final VirtualFile virtualFile = directory.getVirtualFile();
    final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(directory.getProject()).getFileIndex()
      .getSourceRootForFile(virtualFile);
    if (sourceRootForFile == null) return false;
    if (sourceRootForFile.equals(virtualFile)) return false;
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    return aPackage != null && !aPackage.getQualifiedName().isEmpty();
  }
}
