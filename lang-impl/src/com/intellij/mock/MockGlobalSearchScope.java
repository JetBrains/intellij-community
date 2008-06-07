/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.mock;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class MockGlobalSearchScope extends GlobalSearchScope {
  public boolean contains(final VirtualFile file) {
    return true;
  }

  public int compare(final VirtualFile file1, final VirtualFile file2) {
    return 0;
  }

  public boolean isSearchInModuleContent(@NotNull final Module aModule) {
    return true;
  }

  public boolean isSearchInLibraries() {
    return true;
  }
}