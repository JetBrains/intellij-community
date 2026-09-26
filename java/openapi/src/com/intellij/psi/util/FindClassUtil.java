// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class FindClassUtil {
  /**
   * Searches the project for modules that contain the class with the specified full-qualified name within
   * the module dependencies or libraries.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @return the modules that contain the given class in dependencies or libraries.
   */
  public static @NotNull @Unmodifiable Collection<Module> findModulesWithClass(@NotNull Project project, @NonNls @NotNull String qualifiedName) {
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass[] possibleClasses = facade.findClasses(qualifiedName, allScope);
    if (possibleClasses.length == 0) {
      return Collections.emptyList();
    }
    Set<Module> relevantModules = new LinkedHashSet<>();
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
