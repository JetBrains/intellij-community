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
package com.intellij.externalDependencies;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Stores list of external dependencies which are required for project to operate normally. Currently only one kind of dependencies
 * is supported ({@link DependencyOnPlugin}) but in future it may also store required JDKs, application servers and generic path variables.
 *
 * @author nik
 */
public abstract class ExternalDependenciesManager {
  public static ExternalDependenciesManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExternalDependenciesManager.class);
  }

  @NotNull
  public abstract <T extends ProjectExternalDependency> List<T> getDependencies(@NotNull Class<T> aClass);

  @NotNull
  public abstract List<ProjectExternalDependency> getAllDependencies();

  public abstract void setAllDependencies(@NotNull List<ProjectExternalDependency> dependencies);
}
