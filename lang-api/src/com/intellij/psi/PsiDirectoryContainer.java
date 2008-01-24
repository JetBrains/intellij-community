package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author yole
 */
public interface PsiDirectoryContainer {
  /**
   * Returns the array of all directories (under all source roots in the project)
   * corresponding to the package.
   *
   * @return the array of directories.
   */
  @NotNull
  PsiDirectory[] getDirectories();

  /**
   * Returns the array of directories corresponding to the package in the specified search scope.
   *
   * @param scope the scope in which directories are searched.
   * @return the array of directories.
   */
  @NotNull
  PsiDirectory[] getDirectories(@NotNull GlobalSearchScope scope);
}
