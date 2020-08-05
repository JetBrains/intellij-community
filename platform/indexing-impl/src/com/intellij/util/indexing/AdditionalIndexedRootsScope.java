// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public final class AdditionalIndexedRootsScope extends GlobalSearchScope {
  private final GlobalSearchScope myBaseScope;
  @NotNull
  private final IndexableFileSet myFileSet;

  public AdditionalIndexedRootsScope(@NotNull GlobalSearchScope baseScope) {
    this(baseScope, new AdditionalIndexableFileSet());
  }

  public AdditionalIndexedRootsScope(@NotNull GlobalSearchScope baseScope, @NotNull Class<? extends IndexableSetContributor> providerClass) {
    this(baseScope, new AdditionalIndexableFileSet(null, IndexableSetContributor.EP_NAME.findExtension(providerClass)));
  }

  public AdditionalIndexedRootsScope(@NotNull GlobalSearchScope baseScope, @NotNull IndexableFileSet myFileSet) {
    super(baseScope.getProject());
    myBaseScope = baseScope;
    this.myFileSet = myFileSet;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myBaseScope.contains(file) || myFileSet.isInSet(file);
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return myBaseScope.compare(file1, file2);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myBaseScope.isSearchInModuleContent(aModule);
  }

  @Override
  public boolean isSearchInLibraries() {
    return myBaseScope.isSearchInLibraries();
  }
}
