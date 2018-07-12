// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public class JavaFilesSearchScope extends GlobalSearchScope {

  private final PsiManager myPsiManager;

  public JavaFilesSearchScope(@NotNull Project project) {
    super(project);
    myPsiManager = PsiManager.getInstance(project);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (file.isDirectory())
      return false;
    FileViewProvider viewProvider = myPsiManager.findViewProvider(file);
    return viewProvider != null && viewProvider.getLanguages().contains(JavaLanguage.INSTANCE);
  }
}
