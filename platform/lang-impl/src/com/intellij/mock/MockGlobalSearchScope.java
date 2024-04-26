// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

public final class MockGlobalSearchScope extends GlobalSearchScope {
  @Override
  public boolean contains(@NotNull final VirtualFile file) {
    return true;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull final Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }
}