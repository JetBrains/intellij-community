// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import java.util.Collection;
import java.util.Collections;

/**
 * Provides methods to perform high-level modifications of project configuration accordingly with dependency management system used in the
 * project. E.g. if the project is imported from Maven the methods will modify pom.xml files and invoke reimporting to update IDEA's
 * project model. Since importing the changes to IDEA's project model may take a while the method work asynchronously and returns
 * {@link Promise} objects which may be used to be notified when the project configuration is finally updated.
 *
 * @see JavaProjectModelModifier
 */
public abstract class JavaProjectModelModificationService {
  public static JavaProjectModelModificationService getInstance(@NotNull Project project) {
    return project.getService(JavaProjectModelModificationService.class);
  }

  public Promise<Void> addDependency(@NotNull Module from, @NotNull Module to) {
    return addDependency(from, to, DependencyScope.COMPILE);
  }

  public Promise<Void> addDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope) {
    return addDependency(from, to, scope, false);
  }

  public abstract Promise<Void> addDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported);

  public Promise<Void> addDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope) {
    return addDependency(from, library, scope, false);
  }

  public abstract Promise<Void> addDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope, boolean exported);

  public Promise<Void> addDependency(@NotNull Module from, @NotNull ExternalLibraryDescriptor libraryDescriptor) {
    return addDependency(from, libraryDescriptor, DependencyScope.COMPILE);
  }

  public Promise<Void> addDependency(@NotNull Module from, @NotNull ExternalLibraryDescriptor descriptor, @NotNull DependencyScope scope) {
    return addDependency(Collections.singletonList(from), descriptor, scope);
  }

  public abstract Promise<Void> addDependency(@NotNull Collection<? extends Module> from,
                                              @NotNull ExternalLibraryDescriptor libraryDescriptor,
                                              @NotNull DependencyScope scope);

  public abstract Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel languageLevel);
}