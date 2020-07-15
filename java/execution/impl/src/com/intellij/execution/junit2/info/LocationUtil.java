// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class LocationUtil {
  public static boolean isJarAttached(@NotNull Location location, @NotNull final PsiPackage aPackage, final String... fqn) {
    return isJarAttached(location, aPackage.getDirectories(), fqn);
  }

  public static boolean isJarAttached(@NotNull Location location,
                                      final PsiDirectory[] directories,
                                      final String... fqns) {
    final Project project = location.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final Module locationModule = location.getModule();
    VirtualFile locationVirtualFile = location.getVirtualFile();
    String arg2 = locationVirtualFile != null ? locationVirtualFile.getPath() : null;
    if (locationModule != null && !Objects.equals(project.getBasePath(), arg2)) {
      for (String fqn : fqns) {
        if (facade.findClass(fqn, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(locationModule, true)) != null) return true;
      }
    }
    else {
      for (PsiDirectory directory : directories) {
        final Module module = ModuleUtilCore.findModuleForFile(directory.getVirtualFile(), project);
        if (module != null) {
          GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true);
          for (String fqn : fqns) {
            if (facade.findClass(fqn, scope) != null) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
