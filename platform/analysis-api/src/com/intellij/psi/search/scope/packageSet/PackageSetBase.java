// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.DeprecatedMethodException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PackageSetBase implements PackageSet {
  /**
   * @deprecated use {@link PackageSetBase#contains(VirtualFile, Project, NamedScopesHolder)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public boolean contains(@NotNull VirtualFile file, NamedScopesHolder holder) {
    return false;
  }

  public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    DeprecatedMethodException.reportDefaultImplementation(getClass(), "contains(file, project, holder)", "Need proper implementation");
    return contains(file, holder);
  }

  @Override
  public boolean contains(@NotNull PsiFile file, @Nullable NamedScopesHolder holder) {
    VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null && contains(virtualFile, file.getProject(), holder);
  }

  @Nullable
  public static PsiFile getPsiFile(@NotNull VirtualFile file, @NotNull Project project) {
    return PsiManager.getInstance(project).findFile(file);
  }
}
