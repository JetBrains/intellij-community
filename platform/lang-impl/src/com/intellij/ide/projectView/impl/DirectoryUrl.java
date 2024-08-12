// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;

public final class DirectoryUrl extends AbstractUrl {
  private static final @NonNls String ELEMENT_TYPE = "directory";

  public DirectoryUrl(String url, String moduleName) {
    super(url, moduleName, ELEMENT_TYPE);
  }
  public static DirectoryUrl create(PsiDirectory directory) {
    Project project = directory.getProject();
    final VirtualFile virtualFile = directory.getVirtualFile();
    final Module module = ModuleUtil.findModuleForFile(virtualFile, project);
    return new DirectoryUrl(virtualFile.getUrl(), module != null ? module.getName() : null);
  }

  @Override
  public Object[] createPath(final Project project) {
    if (moduleName != null) {
      final Module module =
        ReadAction.compute(() -> ModuleManager.getInstance(project).findModuleByName(moduleName));
      if (module == null) return null;
    }
    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    final VirtualFile file = virtualFileManager.findFileByUrl(url);
    if (file == null) return null;
    final PsiDirectory directory =
      ReadAction.compute(() -> PsiManager.getInstance(project).findDirectory(file));
    if (directory == null) return null;
    return new Object[]{directory};
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
      return new DirectoryUrl(url, moduleName);
  }

  @Override
  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof PsiDirectory) {
      return create((PsiDirectory)element);
    }
    return null;
  }
}
