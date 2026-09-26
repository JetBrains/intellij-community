// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

public final class ResourceFileUtil {
  private ResourceFileUtil() {
  }

  public static @Nullable VirtualFile findResourceFileInDependents(final Module searchFromModule, final String fileName) {
    return findResourceFileInScope(fileName, searchFromModule.getProject(), searchFromModule.getModuleWithDependenciesScope());
  }

  public static @Nullable VirtualFile findResourceFileInProject(final Project project, final String resourceName) {
    return findResourceFileInScope(resourceName, project, GlobalSearchScope.projectScope(project));
  }

  public static @Nullable VirtualFile findResourceFileInScope(final String resourceName,
                                                              final Project project,
                                                              final GlobalSearchScope scope) {
    int index = resourceName.lastIndexOf('/');
    String packageName = index >= 0 ? resourceName.substring(0, index).replace('/', '.') : "";
    final String fileName = index >= 0 ? resourceName.substring(index+1) : resourceName;

    PackageIndex packageIndex = PackageIndex.getInstance(project);
    VirtualFile fileFromDir = packageIndex.getDirsByPackageName(packageName, false)
      .mapping(file -> file.findChild(fileName))
      .filtering(child -> child != null && scope.contains(child))
      .findFirst();
    if (fileFromDir != null) {
      return fileFromDir;
    }
    return packageIndex.getFilesByPackageName(packageName)
      .filtering(f -> fileName.equals(f.getName()) && scope.contains(f))
      .findFirst();
  }
}
