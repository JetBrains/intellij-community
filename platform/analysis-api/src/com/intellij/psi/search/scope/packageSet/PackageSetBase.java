/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PackageSetBase implements PackageSet {
  /**
   * @see PackageSetBase#contains(VirtualFile, Project, NamedScopesHolder)
   */
  @Deprecated
  public abstract boolean contains(VirtualFile file, NamedScopesHolder holder);

  public boolean contains(VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    return contains(file, holder);
  }

  @Override
  public boolean contains(@NotNull PsiFile file, NamedScopesHolder holder) {
    return contains(file.getVirtualFile(), file.getProject(), holder);
  }

  @Nullable
  public static PsiFile getPsiFile(@NotNull VirtualFile file, @NotNull Project project) {
    return PsiManager.getInstance(project).findFile(file);
  }
}
