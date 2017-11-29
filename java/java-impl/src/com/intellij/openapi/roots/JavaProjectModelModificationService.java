/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
 *
 * @author nik
 */
public abstract class JavaProjectModelModificationService {
  public static JavaProjectModelModificationService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, JavaProjectModelModificationService.class);
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

  public abstract Promise<Void> addDependency(@NotNull Collection<Module> from,
                                              @NotNull ExternalLibraryDescriptor libraryDescriptor,
                                              @NotNull DependencyScope scope);

  public abstract Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel languageLevel);
}