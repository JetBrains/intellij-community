// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

public class NullFileReferenceHelper extends FileReferenceHelper {

  public static final NullFileReferenceHelper INSTANCE = new NullFileReferenceHelper();

  @Override
  public PsiFileSystemItem findRoot(final @NotNull Project project, final @NotNull VirtualFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile contentRootForFile = index.getContentRootForFile(file);

    return contentRootForFile != null ? PsiManager.getInstance(project).findDirectory(contentRootForFile) : null;
  }

  @Override
  public @Unmodifiable @NotNull Collection<PsiFileSystemItem> getRoots(final @NotNull Module module) {
    return ContainerUtil.mapNotNull(ModuleRootManager.getInstance(module).getContentRoots(), virtualFile -> PsiManager.getInstance(module.getProject()).findDirectory(virtualFile));
  }

  @Override
  public @NotNull Collection<PsiFileSystemItem> getContexts(final @NotNull Project project, final @NotNull VirtualFile file) {
    final PsiFileSystemItem item = getPsiFileSystemItem(project, file);
    if (item != null) {
      final PsiFileSystemItem parent = item.getParent();
      if (parent != null) {
        return Collections.singleton(parent);
      }
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isMine(final @NotNull Project project, final @NotNull VirtualFile file) {
    return ProjectRootManager.getInstance(project).getFileIndex().isInContent(file);
  }

  @Override
  public boolean isFallback() {
    return true;
  }
}
