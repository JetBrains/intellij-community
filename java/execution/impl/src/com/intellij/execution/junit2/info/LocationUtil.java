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
package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 2/20/12
 */
public class LocationUtil {
  public static boolean isJarAttached(@NotNull Location location, @NotNull final PsiPackage aPackage, final String fqn) {
    return isJarAttached(location, fqn, aPackage.getDirectories());
  }

  public static boolean isJarAttached(@NotNull Location location,
                                      final String fqn,
                                      final PsiDirectory[] directories) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(location.getProject());
    boolean testngJarFound = false;
    final Module locationModule = location.getModule();
    if (locationModule != null) {
      testngJarFound = facade.findClass(fqn, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(locationModule, true)) != null;
    }
    else {
      for (PsiDirectory directory : directories) {
        final Module module = ModuleUtilCore.findModuleForFile(directory.getVirtualFile(), location.getProject());
        if (module != null) {
          if (facade.findClass(fqn, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)) != null) {
            testngJarFound = true;
            break;
          }
        }
      }
    }
    return testngJarFound;
  }
}
