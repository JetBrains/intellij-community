// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.facade;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public interface JvmFacade {

  static @NotNull JvmFacade getInstance(@NotNull Project project) {
    return project.getService(JvmFacade.class);
  }

  /**
   * Searches the specified scope within the project for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope         the scope to search.
   * @return the PSI class, or null if no class with such name is found.
   */
  default @Nullable JvmClass findClass(@NonNls @NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    ProgressManager.checkCanceled();
    return getFirstItem(findClasses(qualifiedName, scope));
  }

  /**
   * Searches the specified scope within the project for classes with the specified full-qualified
   * name and returns all found classes.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope         the scope to search.
   * @return the list of found classes, or an empty array if no classes are found.
   */
  @NotNull
  @Unmodifiable
  List<? extends JvmClass> findClasses(@NonNls @NotNull String qualifiedName, @NotNull GlobalSearchScope scope);
}
