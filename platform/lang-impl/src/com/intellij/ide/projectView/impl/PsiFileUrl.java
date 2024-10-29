// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;

/**
 * @author anna
 * @author Konstantin Bulenkov
 */
public final class PsiFileUrl extends AbstractUrl {
  private static final @NonNls String ELEMENT_TYPE = "psiFile";

  public PsiFileUrl(final String url) {
    super(url, null, ELEMENT_TYPE);
  }

  @Override
  public Object[] createPath(final Project project) {
    final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file == null || !file.isValid()){
      return null;
    }
    return new Object[]{PsiManager.getInstance(project).findFile(file)};
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
      return new PsiFileUrl(url);
  }

  @Override
  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof PsiFile) {
      VirtualFile file = ((PsiFile)element).getVirtualFile();
      if (file != null){
        return new PsiFileUrl(file.getUrl());
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof PsiFileUrl) {
     return StringUtil.equals(url, ((PsiFileUrl)o).url);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return url.hashCode();
  }
}
