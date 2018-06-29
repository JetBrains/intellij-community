// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LogicalRoot;
import com.intellij.util.LogicalRootsManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaVirtualFileQualifiedNameProvider implements CopyReferenceAction.VirtualFileQualifiedNameProvider {
  @Nullable
  @Override
  public String getQualifiedName(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile, false);
    if (module == null || !ModuleType.is(module, JavaModuleType.getModuleType())) {
      return null;
    }

    final LogicalRoot logicalRoot = LogicalRootsManager.getLogicalRootsManager(project).findLogicalRoot(virtualFile);
    VirtualFile logicalRootFile = logicalRoot != null ? logicalRoot.getVirtualFile() : null;
    if (logicalRootFile != null && !virtualFile.equals(logicalRootFile)) {
      return ObjectUtils.assertNotNull(VfsUtilCore.getRelativePath(virtualFile, logicalRootFile, '/'));
    }

    VirtualFile outerMostRoot = null;
    VirtualFile each = virtualFile;
    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
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
