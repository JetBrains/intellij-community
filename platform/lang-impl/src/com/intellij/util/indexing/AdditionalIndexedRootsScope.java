/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  private final IndexableFileSet myFileSet;

  public AdditionalIndexedRootsScope(GlobalSearchScope baseScope) {
    this(baseScope, new AdditionalIndexableFileSet());
  }

  public AdditionalIndexedRootsScope(GlobalSearchScope baseScope, Class<? extends IndexedRootsProvider> providerClass) {
    this(baseScope, new AdditionalIndexableFileSet(IndexedRootsProvider.EP_NAME.findExtension(providerClass)));
  }

  public AdditionalIndexedRootsScope(GlobalSearchScope baseScope, IndexableFileSet myFileSet) {
    super(baseScope.getProject());
    myBaseScope = baseScope;
    this.myFileSet = myFileSet;
  }

  public boolean contains(VirtualFile file) {
    return myBaseScope.contains(file) || myFileSet.isInSet(file);
  }

  @Override
  public boolean isSearchOutsideRootModel() {
    return true;
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
