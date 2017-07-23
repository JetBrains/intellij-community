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
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.util.io.FileUtil.pathsEqual;

/**
 * @author Vladislav.Soroka
 * @since 10/6/2015
 */
public class IdeModelsProviderImpl implements IdeModelsProvider {

  @NotNull
  protected final Project myProject;

  public IdeModelsProviderImpl(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Module[] getModules() {
    return ModuleManager.getInstance(myProject).getModules();
  }

  @NotNull
  @Override
  public Module[] getModules(@NotNull final ProjectData projectData) {
    final List<Module> modules = ContainerUtil.filter(
      getModules(),
      module -> isExternalSystemAwareModule(projectData.getOwner(), module) &&
                StringUtil.equals(projectData.getLinkedExternalProjectPath(), getExternalRootProjectPath(module)));
    return ContainerUtil.toArray(modules, new Module[modules.size()]);
  }

  @NotNull
  @Override
  public OrderEntry[] getOrderEntries(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).getOrderEntries();
  }

  @Nullable
  @Override
  public Module findIdeModule(@NotNull ModuleData module) {
    for (String candidate : suggestModuleNameCandidates(module)) {
      Module ideModule = findIdeModule(candidate);
      if (ideModule != null && isApplicableIdeModule(module, ideModule)) {
        return ideModule;
      }
    }
    return null;
  }

  protected String[] suggestModuleNameCandidates(@NotNull ModuleData module) {
    String prefix = module.getGroup();
    File modulePath = new File(module.getLinkedExternalProjectPath());
    if (modulePath.isFile()) {
      modulePath = modulePath.getParentFile();
    }
    if (modulePath.getParentFile() != null) {
      prefix = modulePath.getParentFile().getName();
    }
    char delimiter = ModuleGrouperKt.isQualifiedModuleNamesEnabled() ? '.' : '-';

    if (prefix == null || StringUtil.startsWith(module.getInternalName(), prefix)) {
      return new String[]{
        module.getInternalName(),
        module.getInternalName() + "~1"};
    }
    else {
      return new String[]{
        module.getInternalName(),
        prefix + delimiter + module.getInternalName(),
        prefix + delimiter + module.getInternalName() + "~1"};
    }
  }

  private static boolean isApplicableIdeModule(@NotNull ModuleData moduleData, @NotNull Module ideModule) {
    for (VirtualFile root : ModuleRootManager.getInstance(ideModule).getContentRoots()) {
      if (pathsEqual(root.getPath(), moduleData.getLinkedExternalProjectPath())) {
        return true;
      }
    }
    return isExternalSystemAwareModule(moduleData.getOwner(), ideModule) &&
           pathsEqual(getExternalProjectPath(ideModule), moduleData.getLinkedExternalProjectPath());
  }

  @Nullable
  @Override
  public Module findIdeModule(@NotNull String ideModuleName) {
    return ModuleManager.getInstance(myProject).findModuleByName(ideModuleName);
  }

  @Nullable
  @Override
  public UnloadedModuleDescription getUnloadedModuleDescription(@NotNull ModuleData moduleData) {
    for (String moduleName : suggestModuleNameCandidates(moduleData)) {
      UnloadedModuleDescription unloadedModuleDescription = ModuleManager.getInstance(myProject).getUnloadedModuleDescription(moduleName);

      // TODO external system module options should be honored to handle duplicated module names issues
      if (unloadedModuleDescription != null) {
        return unloadedModuleDescription;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Library findIdeLibrary(@NotNull LibraryData libraryData) {
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    for (Library ideLibrary : libraryTable.getLibraries()) {
      if (isRelated(ideLibrary, libraryData)) return ideLibrary;
    }
    return null;
  }

  @Nullable
  @Override
  public ModuleOrderEntry findIdeModuleDependency(@NotNull ModuleDependencyData dependency, @NotNull Module module) {
    for (OrderEntry entry : getOrderEntries(module)) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry candidate = (ModuleOrderEntry)entry;
        if (dependency.getInternalName().equals(candidate.getModuleName()) && dependency.getScope().equals(candidate.getScope())) {
          return candidate;
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public OrderEntry findIdeModuleOrderEntry(@NotNull DependencyData data) {
    Module ownerIdeModule = findIdeModule(data.getOwnerModule());
    if (ownerIdeModule == null) return null;

    LibraryDependencyData libraryDependencyData = null;
    ModuleDependencyData moduleDependencyData = null;
    if (data instanceof LibraryDependencyData) {
      libraryDependencyData = (LibraryDependencyData)data;
    }
    else if (data instanceof ModuleDependencyData) {
      moduleDependencyData = (ModuleDependencyData)data;
    }
    else {
      return null;
    }

    for (OrderEntry entry : getOrderEntries(ownerIdeModule)) {
      if (entry instanceof LibraryOrderEntry && libraryDependencyData != null) {
        if (((LibraryOrderEntry)entry).isModuleLevel() && libraryDependencyData.getLevel() != LibraryLevel.MODULE) continue;
        if (StringUtil.isEmpty(((LibraryOrderEntry)entry).getLibraryName())) {
          final Set<String> paths =
            ContainerUtil.map2Set(libraryDependencyData.getTarget().getPaths(LibraryPathType.BINARY), path -> PathUtil.getLocalPath(path));
          final Set<String> entryPaths = ContainerUtil.map2Set(entry.getUrls(OrderRootType.CLASSES),
                                                               s -> PathUtil.getLocalPath(VfsUtilCore.urlToPath(s)));
          if (entryPaths.equals(paths) && ((LibraryOrderEntry)entry).getScope() == data.getScope()) return entry;
          continue;
        }
      }

      String entryName = libraryDependencyData != null ? libraryDependencyData.getInternalName() : moduleDependencyData.getInternalName();
      if (entryName.equals(entry.getPresentableName()) &&
          (!(entry instanceof ExportableOrderEntry) || ((ExportableOrderEntry)entry).getScope() == data.getScope())) {
        return entry;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public VirtualFile[] getContentRoots(Module module) {
    return ModuleRootManager.getInstance(module).getContentRoots();
  }

  @NotNull
  @Override
  public VirtualFile[] getSourceRoots(Module module) {
    return ModuleRootManager.getInstance(module).getSourceRoots();
  }

  @NotNull
  @Override
  public VirtualFile[] getSourceRoots(Module module, boolean includingTests) {
    return ModuleRootManager.getInstance(module).getSourceRoots(includingTests);
  }

  @NotNull
  @Override
  public Library[] getAllLibraries() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraries();
  }

  @Nullable
  @Override
  public Library getLibraryByName(String name) {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(name);
  }

  @NotNull
  @Override
  public String[] getLibraryUrls(@NotNull Library library, @NotNull OrderRootType type) {
    return library.getUrls(type);
  }

  @NotNull
  public List<Module> getAllDependentModules(@NotNull Module module) {
    return ModuleUtilCore.getAllDependentModules(module);
  }
}
