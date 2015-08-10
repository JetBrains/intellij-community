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
package com.intellij.openapi.roots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public abstract class ProjectModelModificationService {
  public static ProjectModelModificationService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectModelModificationService.class);
  }

  public void addDependency(@NotNull Module from, @NotNull Module to) {
    addDependency(from, to, DependencyScope.COMPILE);
  }

  public abstract void addDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope);

  public void addDependency(@NotNull Module from, @NotNull ExternalLibraryDescriptor libraryDescriptor) {
    addDependency(from, libraryDescriptor, DependencyScope.COMPILE);
  }

  public void addDependency(Module from, ExternalLibraryDescriptor descriptor, DependencyScope scope) {
    addDependency(Collections.singletonList(from), descriptor, scope);
  }

  public abstract void addDependency(@NotNull Collection<Module> from, @NotNull ExternalLibraryDescriptor libraryDescriptor,
                                     @NotNull DependencyScope scope);

  public abstract void addDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope);
}
