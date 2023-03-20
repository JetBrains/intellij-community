// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class JavaVirtualFileQualifiedNameProvider implements VirtualFileQualifiedNameProvider {
  @Nullable
  @Override
  public String getQualifiedName(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile, false);
    if (module == null || !ModuleType.is(module, JavaModuleType.getModuleType())) {
      return null;
    }

    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile sourceRoot = index.getSourceRootForFile(virtualFile);
    if (sourceRoot != null && !sourceRoot.equals(virtualFile)) {
      return Objects.requireNonNull(VfsUtilCore.getRelativePath(virtualFile, sourceRoot, '/'));
    }

    VirtualFile outerMostRoot = null;
    VirtualFile each = virtualFile;
    while (each != null && (each = index.getContentRootForFile(each, false)) != null) {
      outerMostRoot = each;
      each = each.getParent();
    }

    if (outerMostRoot != null && !outerMostRoot.equals(virtualFile)) {
      String relative = VfsUtilCore.getRelativePath(virtualFile, outerMostRoot, '/');
      if (relative != null) {
        return relative;
      }
    }

    return virtualFile.getPath();
  }
}
