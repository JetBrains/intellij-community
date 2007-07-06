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
    throw new UnsupportedOperationException("Method contains is not yet implemented in " + getClass().getName());
  }

  public int compare(final VirtualFile file1, final VirtualFile file2) {
    throw new UnsupportedOperationException("Method compare is not yet implemented in " + getClass().getName());
  }

  public boolean isSearchInModuleContent(@NotNull final Module aModule) {
    throw new UnsupportedOperationException("Method isSearchInModuleContent is not yet implemented in " + getClass().getName());
  }

  public boolean isSearchInLibraries() {
    throw new UnsupportedOperationException("Method isSearchInLibraries is not yet implemented in " + getClass().getName());
  }
}
