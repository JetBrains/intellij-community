// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.util.io.FileUtil.pathsEqual;
import static com.intellij.openapi.util.text.StringUtil.*;

/**
 * @author Vladislav.Soroka
 */
public class IdeModelsProviderImpl implements IdeModelsProvider {
  private static final Logger LOG = Logger.getInstance(IdeModelsProviderImpl.class);

  @NotNull
  protected final Project myProject;

  @NotNull
  private final Map<ModuleData, Module> myIdeModulesCache = new WeakHashMap<>();

  private final Map<Module, Map<String, List<ModuleOrderEntry>>> myIdeModuleToModuleDepsCache = new WeakHashMap<>();

  public IdeModelsProviderImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Module @NotNull [] getModules() {
    return ModuleManager.getInstance(myProject).getModules();
  }

  @Override
  public Module @NotNull [] getModules(@NotNull final ProjectData projectData) {
    final List<Module> modules = ContainerUtil.filter(
      getModules(),
      module -> isExternalSystemAwareModule(projectData.getOwner(), module) &&
                StringUtil.equals(projectData.getLinkedExternalProjectPath(), getExternalRootProjectPath(module)));
    return modules.toArray(Module.EMPTY_ARRAY);
  }

  @Override
  public OrderEntry @NotNull [] getOrderEntries(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).getOrderEntries();
  }

  @Nullable
  @Override
  public Module findIdeModule(@NotNull ModuleData module) {
    Module cachedIdeModule = myIdeModulesCache.get(module);
    if (cachedIdeModule == null) {
      for (String candidate : suggestModuleNameCandidates(module)) {
        Module ideModule = findIdeModule(candidate);
        if (ideModule != null && isApplicableIdeModule(module, ideModule)) {
          myIdeModulesCache.put(module, ideModule);
          return ideModule;
        }
      }
    }
    else {
      return cachedIdeModule;
    }
    return null;
  }

  protected Iterable<String> suggestModuleNameCandidates(@NotNull ModuleData module) {
    ExternalProjectSettings settings = getSettings(myProject,
                                                   module.getOwner()).getLinkedProjectSettings(module.getLinkedExternalProjectPath());
    char delimiter = settings != null && !settings.isUseQualifiedModuleNames() ? '-' : '.';
    return new ModuleNameGenerator(module, delimiter).generate();
  }

  private static boolean isApplicableIdeModule(@NotNull ModuleData moduleData, @NotNull Module ideModule) {
    for (VirtualFile root : ModuleRootManager.getInstance(ideModule).getContentRoots()) {
      if (VfsUtilCore.pathEqualsTo(root, moduleData.getLinkedExternalProjectPath())) {
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
    Map<String, List<ModuleOrderEntry>> namesToEntries = myIdeModuleToModuleDepsCache.computeIfAbsent(
      module, (m) -> Arrays.stream(getOrderEntries(m))
                           .filter(ModuleOrderEntry.class::isInstance)
                           .map(ModuleOrderEntry.class::cast)
                           .collect(Collectors.groupingBy(ModuleOrderEntry::getModuleName))
    );

    List<ModuleOrderEntry> candidates = namesToEntries.get(dependency.getInternalName());

    if (candidates == null) {
      return null;
    }

    for (ModuleOrderEntry candidate : candidates) {
      if (candidate.getScope().equals(dependency.getScope())) {
        return candidate;
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
        if (isEmpty(((LibraryOrderEntry)entry).getLibraryName())) {
          final Set<String> paths = ContainerUtil.map2Set(libraryDependencyData.getTarget().getPaths(LibraryPathType.BINARY),
                                                          PathUtil::getLocalPath);
          final Set<String> entryPaths = ContainerUtil.map2Set(((LibraryOrderEntry)entry).getRootUrls(OrderRootType.CLASSES),
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
  public Map<LibraryOrderEntry, LibraryDependencyData> findIdeModuleLibraryOrderEntries(@NotNull ModuleData moduleData,
                                                                                        @NotNull List<LibraryDependencyData> libraryDependencyDataList) {
    if (libraryDependencyDataList.isEmpty()) return Collections.emptyMap();
    Module ownerIdeModule = findIdeModule(moduleData);
    if (ownerIdeModule == null) return Collections.emptyMap();
    Map<Set<String>, LibraryDependencyData> libraryDependencyDataMap = new HashMap<>();
    for (LibraryDependencyData libraryDependencyData : libraryDependencyDataList) {
      if (libraryDependencyData.getLevel() == LibraryLevel.PROJECT) {
        LOG.warn("Library data \"" + libraryDependencyData.getInternalName() + "\" not a module level dependency");
        continue;
      }
      if (libraryDependencyData.getOwnerModule() != moduleData) {
        LOG.warn("Library data \"" + libraryDependencyData.getInternalName() + "\" not belong to the module: " + ownerIdeModule.getName());
        continue;
      }
      libraryDependencyDataMap.put(ContainerUtil.map2Set(libraryDependencyData.getTarget().getPaths(LibraryPathType.BINARY),
                                                         PathUtil::getLocalPath), libraryDependencyData);
    }

    Map<LibraryOrderEntry, LibraryDependencyData> result = new HashMap<>();
    for (OrderEntry entry : getOrderEntries(ownerIdeModule)) {
      if (entry instanceof LibraryOrderEntry libraryOrderEntry) {
        if (!libraryOrderEntry.isModuleLevel()) continue;
        final Set<String> entryPaths = ContainerUtil.map2Set(((LibraryOrderEntry)entry).getRootUrls(OrderRootType.CLASSES),
                                                             s -> PathUtil.getLocalPath(VfsUtilCore.urlToPath(s)));
        LibraryDependencyData libraryDependencyData = libraryDependencyDataMap.get(entryPaths);
        if (libraryDependencyData != null && libraryOrderEntry.getScope() == libraryDependencyData.getScope()) {
          result.put(libraryOrderEntry, libraryDependencyData);
        }
      }
    }
    return result;
  }

  @Override
  public VirtualFile @NotNull [] getContentRoots(Module module) {
    return ModuleRootManager.getInstance(module).getContentRoots();
  }

  @Override
  public VirtualFile @NotNull [] getSourceRoots(Module module) {
    return ModuleRootManager.getInstance(module).getSourceRoots();
  }

  @Override
  public VirtualFile @NotNull [] getSourceRoots(Module module, boolean includingTests) {
    return ModuleRootManager.getInstance(module).getSourceRoots(includingTests);
  }

  @Override
  public Library @NotNull [] getAllLibraries() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraries();
  }

  @Nullable
  @Override
  public Library getLibraryByName(String name) {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(name);
  }

  @Override
  public String @NotNull [] getLibraryUrls(@NotNull Library library, @NotNull OrderRootType type) {
    return library.getUrls(type);
  }

  @Override
  @NotNull
  public List<Module> getAllDependentModules(@NotNull Module module) {
    return ModuleUtilCore.getAllDependentModules(module);
  }

  private static class ModuleNameGenerator {
    private static final int MAX_FILE_DEPTH = 3;
    private static final int MAX_NUMBER_SEQ = 2;
    private final ModuleData myModule;
    private final char myDelimiter;

    ModuleNameGenerator(@NotNull ModuleData module, char delimiter) {
      myModule = module;
      myDelimiter = delimiter;
    }

    Iterable<String> generate() {
      List<String> names = new ArrayList<>();
      String prefix = myModule.getGroup();
      File modulePath = new File(myModule.getLinkedExternalProjectPath());
      if (modulePath.isFile()) {
        modulePath = modulePath.getParentFile();
      }

      if (prefix == null || startsWith(myModule.getInternalName(), prefix)) {
        names.add(myModule.getInternalName());
      }
      else {
        names.add(myModule.getInternalName());
        names.add(prefix + myDelimiter + myModule.getInternalName());
      }

      String name = names.get(0);
      List<String> pathParts = FileUtil.splitPath(FileUtil.toSystemDependentName(modulePath.getPath()));
      StringBuilder nameBuilder = new StringBuilder();
      String duplicateCandidate = name;
      for (int i = pathParts.size() - 1, j = 0; i >= 0 && j < MAX_FILE_DEPTH; i--, j++) {
        String part = pathParts.get(i);

        // do not add prefix which was already included into the name (e.g. as a result of deduplication on the external system side)
        boolean isAlreadyIncluded = false;
        if (!duplicateCandidate.isEmpty()) {
          if (duplicateCandidate.equals(part) ||
              duplicateCandidate.endsWith(myDelimiter + part) ||
              duplicateCandidate.endsWith('_' + part)) {
            j--;
            duplicateCandidate = trimEnd(trimEnd(trimEnd(duplicateCandidate, part), myDelimiter), '_');
            isAlreadyIncluded = true;
          }
          else {
            if ((name.startsWith(part) || i > 1 && name.startsWith(pathParts.get(i - 1) + myDelimiter + part))) {
              j--;
              isAlreadyIncluded = true;
            }
            else {
              duplicateCandidate = "";
            }
          }
        }
        if (isAlreadyIncluded) continue;

        nameBuilder.insert(0, part + myDelimiter);
        names.add(nameBuilder + name);
      }

      String namePrefix = ContainerUtil.getLastItem(names);
      return new Iterable<>() {
        @NotNull
        @Override
        public Iterator<String> iterator() {
          return ContainerUtil.concatIterators(names.iterator(), new Iterator<>() {
            int current = 0;

            @Override
            public boolean hasNext() {
              return current < MAX_NUMBER_SEQ;
            }

            @Override
            public String next() {
              current++;
              return namePrefix + '~' + current;
            }
          });
        }
      };
    }
  }
}
