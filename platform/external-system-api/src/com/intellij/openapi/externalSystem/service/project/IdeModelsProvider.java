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
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public interface IdeModelsProvider {
  Module @NotNull [] getModules();

  Module @NotNull [] getModules(@NotNull ProjectData projectData);

  OrderEntry @NotNull [] getOrderEntries(@NotNull Module module);

  @Nullable
  Module findIdeModule(@NotNull ModuleData module);

  @Nullable
  Module findIdeModule(@NotNull String ideModuleName);

  @Nullable
  UnloadedModuleDescription getUnloadedModuleDescription(@NotNull ModuleData moduleData);

  @Nullable
  Library findIdeLibrary(@NotNull LibraryData libraryData);

  @Nullable
  ModuleOrderEntry findIdeModuleDependency(@NotNull ModuleDependencyData dependency, @NotNull Module module);

  @Nullable
  OrderEntry findIdeModuleOrderEntry(@NotNull DependencyData data);

  @NotNull
  Map<LibraryOrderEntry, LibraryDependencyData> findIdeModuleLibraryOrderEntries(@NotNull ModuleData moduleData,
                                                                                 @NotNull List<LibraryDependencyData> libraryDependencyDataList);

  VirtualFile @NotNull [] getContentRoots(Module module);

  VirtualFile @NotNull [] getSourceRoots(Module module);

  VirtualFile @NotNull [] getSourceRoots(Module module, boolean includingTests);

  Library @NotNull [] getAllLibraries();

  @Nullable
  Library getLibraryByName(String name);

  String @NotNull [] getLibraryUrls(@NotNull Library library, @NotNull OrderRootType type);

  @NotNull
  List<Module> getAllDependentModules(@NotNull Module module);
}
