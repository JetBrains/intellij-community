/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.openapi.components.LoadCancelledException;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.graph.Graph;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public abstract class ModuleManager {
  public static ModuleManager getInstance(Project project) {
    return project.getComponent(ModuleManager.class);
  }

  @NotNull public abstract Module newModule(@NotNull String filePath) throws LoadCancelledException;

  @NotNull public abstract Module newModule(@NotNull String filePath, ModuleType moduleType) throws LoadCancelledException;

  @NotNull public abstract Module loadModule(@NotNull String filePath) throws InvalidDataException, IOException, JDOMException, ModuleWithNameAlreadyExists, ModuleCircularDependencyException, LoadCancelledException;

  public abstract void disposeModule(@NotNull Module module);

  @NotNull public abstract Module[] getModules();

  @Nullable public abstract Module findModuleByName(@NotNull String name);

  @NotNull public abstract Module[] getSortedModules();

  @NotNull public abstract Comparator<Module> moduleDependencyComparator();

  /**
   * @return list of <i>modules that depend on</i> given module.
   */
  @NotNull public abstract List<Module> getModuleDependentModules(@NotNull Module module);

  public abstract boolean isModuleDependent(@NotNull Module module, @NotNull Module onModule);

  public abstract void addModuleListener(@NotNull ModuleListener listener);

  public abstract void removeModuleListener(@NotNull ModuleListener listener);

  @NotNull public abstract Graph<Module> moduleGraph();

  @NotNull public abstract ModifiableModuleModel getModifiableModel();

  public abstract void dispatchPendingEvent(@NotNull ModuleListener listener);

  @Nullable public abstract String[] getModuleGroupPath(@NotNull Module module);
}
