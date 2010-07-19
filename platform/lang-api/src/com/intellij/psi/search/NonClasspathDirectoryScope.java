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

package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class NonClasspathDirectoryScope extends GlobalSearchScope {
  private final VirtualFile myRoot;

  public NonClasspathDirectoryScope(@NotNull VirtualFile root) {
    myRoot = root;
  }

  @Override
  public boolean isSearchOutsideRootModel() {
    return true;
  }

  @NotNull
  public static GlobalSearchScope compose(List<VirtualFile> roots) {
    if (roots.isEmpty()) {
      return EMPTY_SCOPE;
    }

    GlobalSearchScope scope = new NonClasspathDirectoryScope(roots.get(0));
    for (int i = 1; i < roots.size(); i++) {
      scope = scope.uniteWith(new NonClasspathDirectoryScope(roots.get(i)));
    }
    return scope;
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
