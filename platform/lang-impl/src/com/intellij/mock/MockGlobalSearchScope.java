// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public final class MockGlobalSearchScope extends GlobalSearchScope {
  @Override
  public boolean contains(final @NotNull VirtualFile file) {
    return true;
  }

  @Override
  public boolean isSearchInModuleContent(final @NotNull Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }
}