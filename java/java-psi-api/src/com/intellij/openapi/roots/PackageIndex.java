// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

/**
 * Provides a possibility to query the directories corresponding to a specific Java package name.
 */
public abstract class PackageIndex {
  public static PackageIndex getInstance(@NotNull Project project) {
    return project.getService(PackageIndex.class);
  }

  /**
   * Returns all directories in content sources and libraries (and optionally library sources)
   * corresponding to the given package name.
   *
   * @param packageName           the name of the package for which directories are requested.
   * @param includeLibrarySources if true, directories under library sources are included in the returned list.
   * @return the list of directories.
   */
  @NotNull
  public abstract VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources);

  /**
   * Returns all directories in content sources and libraries (and optionally library sources)
   * corresponding to the given package name as a query object (allowing to perform partial iteration of the results).
   *
   * @param packageName           the name of the package for which directories are requested.
   * @param includeLibrarySources if true, directories under library sources are included in the returned list.
   * @return the query returning the list of directories.
   */
  @NotNull
  public abstract Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources);
}
