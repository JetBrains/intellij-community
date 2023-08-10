// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public interface PsiDirectoryContainer extends PsiNamedElement {
  /**
   * Returns the array of all directories (under all source roots in the project)
   * corresponding to the package.
   *
   * @return the array of directories.
   */
  PsiDirectory @NotNull [] getDirectories();

  /**
   * Returns the array of directories corresponding to the package in the specified search scope.
   *
   * @param scope the scope in which directories are searched.
   * @return the array of directories.
   */
  PsiDirectory @NotNull [] getDirectories(@NotNull GlobalSearchScope scope);
}
