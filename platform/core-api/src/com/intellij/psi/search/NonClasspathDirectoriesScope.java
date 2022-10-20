/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.core.CoreBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NonClasspathDirectoriesScope extends GlobalSearchScope {
  private final Set<VirtualFile> myRoots;

  public NonClasspathDirectoriesScope(@NotNull Collection<? extends VirtualFile> roots) {
    myRoots = new HashSet<>(roots);
  }

  @NotNull
  public static GlobalSearchScope compose(@NotNull List<? extends VirtualFile> roots) {
    if (roots.isEmpty()) {
      return EMPTY_SCOPE;
    }

    return new NonClasspathDirectoriesScope(roots);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return VfsUtilCore.isUnder(file, myRoots);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NonClasspathDirectoriesScope)) return false;

    NonClasspathDirectoriesScope that = (NonClasspathDirectoriesScope)o;

    if (!myRoots.equals(that.myRoots)) return false;

    return true;
  }

  @Override
  public int calcHashCode() {
    return myRoots.hashCode();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    if (myRoots.size() == 1) {
      VirtualFile root = myRoots.iterator().next();
      return CoreBundle.message("scope.display.name.directory.0", root.getName());
    }
    return CoreBundle.message("scope.display.name.directories.0", StringUtil.join(myRoots, file -> "'" + file.getName() + "'", ", "));
  }
}
