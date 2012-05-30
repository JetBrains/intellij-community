/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CollectionQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CorePackageIndex extends PackageIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.core.CorePackageIndex");

  private final List<VirtualFile> myClasspath = new ArrayList<VirtualFile>();

  public CorePackageIndex() {
  }

  private List<VirtualFile> roots() {
    return myClasspath;
  }

  private List<VirtualFile> findDirectoriesByPackageName(String packageName) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    String dirName = packageName.replace(".", "/");
    for (VirtualFile root : roots()) {
      VirtualFile classDir = root.findFileByRelativePath(dirName);
      if (classDir != null) {
        result.add(classDir);
      }
    }
    return result;
  }

  @Override
  public VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
  }

  @Override
  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return new CollectionQuery<VirtualFile>(findDirectoriesByPackageName(packageName));
  }

  public void addToClasspath(VirtualFile root) {
    myClasspath.add(root);
  }
}
