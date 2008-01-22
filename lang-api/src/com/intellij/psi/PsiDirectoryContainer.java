package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

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
}
