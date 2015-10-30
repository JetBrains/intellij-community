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
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 9/11/2015
 */
public interface IdeModelsProvider {
  @NotNull
  Module[] getModules();

  @NotNull
  Module[] getModules(@NotNull ProjectData projectData);

  @NotNull
  OrderEntry[] getOrderEntries(@NotNull Module module);

  @Nullable
  Module findIdeModule(@NotNull ModuleData module);

  @Nullable
  Module findIdeModule(@NotNull String ideModuleName);

  @Nullable
  Library findIdeLibrary(@NotNull LibraryData libraryData);

  @Nullable
  ModuleOrderEntry findIdeModuleDependency(@NotNull ModuleDependencyData dependency, @NotNull Module module);

  @Nullable
  OrderEntry findIdeModuleOrderEntry(@NotNull DependencyData data);

  @NotNull
  VirtualFile[] getContentRoots(Module module);

  @NotNull
  VirtualFile[] getSourceRoots(Module module);

  @NotNull
  VirtualFile[] getSourceRoots(Module module, boolean includingTests);

  @NotNull
  Library[] getAllLibraries();

  @Nullable
  Library getLibraryByName(String name);

  @NotNull
  String[] getLibraryUrls(@NotNull Library library, @NotNull OrderRootType type);

  @NotNull
  List<Module> getAllDependentModules(@NotNull Module module);
}
