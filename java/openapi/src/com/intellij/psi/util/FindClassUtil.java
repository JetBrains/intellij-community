/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class FindClassUtil {
  /**
   * Searches the project for modules that contain the class with the specified full-qualified name within
   * the module dependencies or libraries.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @return the modules that contain the given class in dependencies or libraries.
   */
  @NotNull
  public static Collection<Module> findModulesWithClass(@NotNull Project project, @NonNls @NotNull String qualifiedName) {
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass[] possibleClasses = facade.findClasses(qualifiedName, allScope);
    if (possibleClasses.length == 0) {
      return Collections.emptyList();
    }
    Set<Module> relevantModules = ContainerUtil.newLinkedHashSet();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiClass aClass : possibleClasses) {
      VirtualFile classFile = aClass.getContainingFile().getVirtualFile();
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(classFile)) {
        relevantModules.add(orderEntry.getOwnerModule());
      }
    }
    return relevantModules;
  }
}
