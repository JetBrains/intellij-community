// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import org.jetbrains.annotations.NotNull;

class JavaAutoModuleFilterScope extends DelegatingGlobalSearchScope {
  JavaAutoModuleFilterScope(@NotNull GlobalSearchScope baseScope) {
    super(baseScope);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (!super.contains(file)) {
      return false;
    }

    VirtualFile root = file;
    if (!file.isDirectory()) {
      root = file.getParent().getParent();
      if (root == null) {
        return false;
      }
      Project project = getProject();
      if (project == null) {
        return false;
      }
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      if (!root.equals(fileIndex.getSourceRootForFile(file)) && !root.equals(fileIndex.getClassRootForFile(file))) {
        return false;
      }
    }
    if (JavaMultiReleaseUtil.findVersionSpecificFile(root, PsiJavaModule.MODULE_INFO_CLS_FILE, LanguageLevel.HIGHEST) != null) {
      return false;
    }

    return true;
  }
}