package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class NonClasspathDirectoryScope extends GlobalSearchScope {
  private final VirtualFile myRoot;

  public NonClasspathDirectoryScope(@NotNull VirtualFile root) {
    myRoot = root;
  }

  @Override
  public boolean contains(VirtualFile file) {
    return VfsUtil.isAncestor(myRoot, file, false);
  }

  @Override
  public int compare(VirtualFile file1, VirtualFile file2) {
    return 0;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }
}
