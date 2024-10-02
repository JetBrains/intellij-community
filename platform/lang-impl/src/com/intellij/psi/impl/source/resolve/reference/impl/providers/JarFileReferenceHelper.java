// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
@ApiStatus.Internal
public final class JarFileReferenceHelper extends FileReferenceHelper {

  @Override
  public PsiFileSystemItem getPsiFileSystemItem(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }

  @Override
  public @NotNull Collection<PsiFileSystemItem> getRoots(@NotNull Module module) {
    return PsiFileReferenceHelper.getContextsForScope(module.getProject(), "", module.getModuleWithDependenciesScope());
  }

  @Override
  public @NotNull Collection<PsiFileSystemItem> getContexts(@NotNull Project project, @NotNull VirtualFile file) {
    return Collections.emptyList();
  }

  @Override
  public boolean isMine(@NotNull Project project, @NotNull VirtualFile file) {
    return ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(file);
  }
}
