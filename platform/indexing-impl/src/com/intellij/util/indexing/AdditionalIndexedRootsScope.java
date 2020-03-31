/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class AdditionalIndexedRootsScope extends GlobalSearchScope {
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
