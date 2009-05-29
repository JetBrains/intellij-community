/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.indexing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
public class AdditionalIndexedRootsScope extends GlobalSearchScope {
  private final GlobalSearchScope myBaseScope;
  private final Set<String> myIndexedFiles;

  public AdditionalIndexedRootsScope(GlobalSearchScope baseScope, Class<? extends IndexedRootsProvider> providerClass) {
    super(baseScope.getProject());
    myBaseScope = baseScope;
    myIndexedFiles = IndexedRootsProvider.EP_NAME.findExtension(providerClass).getRootsToIndex();
  }

  public boolean contains(VirtualFile file) {
    return myBaseScope.contains(file) || myIndexedFiles.contains(file.getUrl());
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    return myBaseScope.compare(file1, file2);
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myBaseScope.isSearchInModuleContent(aModule);
  }

  public boolean isSearchInLibraries() {
    return myBaseScope.isSearchInLibraries();
  }
}
