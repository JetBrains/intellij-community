package com.intellij.util.xml;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class ModuleContentRootSearchScope extends GlobalSearchScope {
  private final ModuleRootManager myRootManager;
  private final Module myModule;

  public ModuleContentRootSearchScope(final Module module) {
    myRootManager = ModuleRootManager.getInstance(module);
    myModule = module;
  }

  public boolean contains(final VirtualFile file) {
    return myRootManager.getFileIndex().isInContent(file);
  }

  public int compare(final VirtualFile file1, final VirtualFile file2) {
    return 0;
  }

  public boolean isSearchInModuleContent(@NotNull final Module aModule) {
    return aModule == myModule;
  }

  public boolean isSearchInLibraries() {
    return false;
  }
}
