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
package com.intellij.openapi.roots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public abstract class PackageIndex {
  public static PackageIndex getInstance(Project project) {
    return ServiceManager.getService(project, PackageIndex.class);
  }

  /**
   * Returns all directories in content sources and libraries (and optionally library sources)
   * corresponding to the given package name.
   *
   * @param packageName           the name of the package for which directories are requested.
   * @param includeLibrarySources if true, directories under library sources are included in the returned list.
   * @return the list of directories.
   */
  public abstract VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources);

  public abstract Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources);
}
