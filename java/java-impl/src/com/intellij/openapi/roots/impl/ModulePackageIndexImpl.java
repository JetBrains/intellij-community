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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModulePackageIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FilteredQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public class ModulePackageIndexImpl extends ModulePackageIndex {
  private final ModuleFileIndex myModuleFileIndex;
  private final DirectoryIndex myDirectoryIndex;

  public ModulePackageIndexImpl(ModuleRootManager moduleRootManager, DirectoryIndex directoryIndex) {
    myModuleFileIndex = moduleRootManager.getFileIndex();
    myDirectoryIndex = directoryIndex;
  }

  private final Condition<VirtualFile> myDirCondition = new Condition<VirtualFile>() {
    @Override
    public boolean value(final VirtualFile dir) {
      return dir.isValid() && myModuleFileIndex.getOrderEntryForFile(dir) != null;
    }
  };

  @Override
  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return new FilteredQuery<>(myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources), myDirCondition);
  }

  @Override
  public VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
  }
}
