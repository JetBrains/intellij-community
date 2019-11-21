// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
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
      Project project = getProject();
      if (project == null || !root.equals(ProjectFileIndex.getInstance(project).getSourceRootForFile(file))) {
        return false;
      }
    }
    if (JavaModuleNameIndex.descriptorFile(root) != null) {
      return false;
    }

    return true;
  }
}