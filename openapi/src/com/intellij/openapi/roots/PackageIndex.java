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
