package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.util.ArtifactInfo;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 2/6/12 3:28 PM
 */
public class ProjectStructureHelper {

  @NotNull private final PlatformFacade                myFacade;
  @NotNull private final ExternalLibraryPathTypeMapper myLibraryPathTypeMapper;

  public ProjectStructureHelper(@NotNull PlatformFacade facade, @NotNull ExternalLibraryPathTypeMapper mapper) {
    myFacade = facade;
    myLibraryPathTypeMapper = mapper;
  }

  @Nullable
  public Module findIdeModule(@NotNull ModuleData module, @NotNull Project ideProject) {
    return findIdeModule(module.getName(), ideProject);
  }

  @Nullable
  public Module findIdeModule(@NotNull String ideModuleName, @NotNull Project ideProject) {
    for (Module module : myFacade.getModules(ideProject)) {
      if (ideModuleName.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }

  @Nullable
  public ModuleAwareContentRoot findIdeContentRoot(@NotNull DataNode<ContentRootData> node, @NotNull Project ideProject) {
    ModuleData moduleData = node.getData(ProjectKeys.MODULE);
    if (moduleData == null) {
      return null;
    }
    final Module module = findIdeModule(moduleData.getName(), ideProject);
    if (module == null) {
      return null;
    }
    for (ModuleAwareContentRoot contentRoot : myFacade.getContentRoots(module)) {
      final VirtualFile file = contentRoot.getFile();
      if (node.getData().getRootPath().equals(file.getPath())) {
        return contentRoot;
      }
    }
    return null;
  }

  @Nullable
  public Library findIdeLibrary(@NotNull final LibraryData library, @NotNull Project ideProject) {
    return findIdeLibrary(library.getName(), ideProject);
  }

  /**
   * Gradle library names follow the following pattern: {@code '[base library name]-[library-version]'}.
   * <p/>
   * This methods serves as an utility which tries to find a library by it's given base name.
   *
   * @param baseName    base name of the target library
   * @param ideProject  target ide project
   * @return            target library for the given base name if there is one and only one library for it;
   *                    <code>null</code> otherwise (if there are no libraries or more than one library for the given base name) 
   */
  @Nullable
  public Library findIdeLibraryByBaseName(@NotNull String baseName, @NotNull Project ideProject) {
    final LibraryTable libraryTable = myFacade.getProjectLibraryTable(ideProject);
    Library result = null;
    for (Library library : libraryTable.getLibraries()) {
      ArtifactInfo info = ExternalSystemApiUtil.parseArtifactInfo(ExternalSystemApiUtil.getLibraryName(library));
      if (info == null || !baseName.equals(info.getName())) {
        continue;
      }
      if (result != null) {
        return null;
      }
      result = library;
    }
    return result;
  }

  @Nullable
  public Library findIdeLibrary(@NotNull String libraryName, @NotNull Project ideProject) {
    final LibraryTable libraryTable = myFacade.getProjectLibraryTable(ideProject);
    for (Library ideLibrary : libraryTable.getLibraries()) {
      if (libraryName.equals(ExternalSystemApiUtil.getLibraryName(ideLibrary))) {
        return ideLibrary;
      }
    }
    return null;
  }

  @Nullable
  public Library findIdeLibrary(@NotNull String libraryName,
                                @NotNull OrderRootType jarType,
                                @NotNull String jarPath,
                                @NotNull Project ideProject)
  {
    Library library = findIdeLibrary(libraryName, ideProject);
    if (library == null) {
      return null;
    }
    for (VirtualFile file : library.getFiles(jarType)) {
      if (jarPath.equals(ExternalSystemApiUtil.getLocalFileSystemPath(file))) {
        return library;
      }
    }
    return null;
  }

  @Nullable
  public LibraryOrderEntry findIdeLibraryDependency(@NotNull final String moduleName,
                                                    @NotNull final String libraryName,
                                                    @NotNull Project ideProject)
  {
    final Module ideModule = findIdeModule(moduleName, ideProject);
    if (ideModule == null) {
      return null;
    }
    RootPolicy<LibraryOrderEntry> visitor = new RootPolicy<LibraryOrderEntry>() {
      @Override
      public LibraryOrderEntry visitLibraryOrderEntry(LibraryOrderEntry ideDependency, LibraryOrderEntry value) {
        if (libraryName.equals(ideDependency.getLibraryName())) {
          return ideDependency;
        }
        return value;
      }
    };
    for (OrderEntry entry : myFacade.getOrderEntries(ideModule)) {
      final LibraryOrderEntry result = entry.accept(visitor, null);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  public ModuleLibraryOrderEntryImpl findIdeModuleLocalLibraryDependency(@NotNull final String moduleName,
                                                                         @NotNull final String libraryName,
                                                                         @NotNull Project ideProject)
  {
    final Module ideModule = findIdeModule(moduleName, ideProject);
    if (ideModule == null) {
      return null;
    }
    RootPolicy<ModuleLibraryOrderEntryImpl> visitor = new RootPolicy<ModuleLibraryOrderEntryImpl>() {
      @Override
      public ModuleLibraryOrderEntryImpl visitLibraryOrderEntry(LibraryOrderEntry ideDependency, ModuleLibraryOrderEntryImpl value) {
        Library library = ideDependency.getLibrary();
        if (library == null) {
          return value;
        }
        if (ideDependency instanceof ModuleLibraryOrderEntryImpl && libraryName.equals(ExternalSystemApiUtil.getLibraryName(library))) {
          return (ModuleLibraryOrderEntryImpl)ideDependency;
        }
        return value;
      }
    };
    for (OrderEntry entry : myFacade.getOrderEntries(ideModule)) {
      final ModuleLibraryOrderEntryImpl result = entry.accept(visitor, null);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public LibraryOrderEntry findIdeLibraryDependency(@NotNull final String libraryName,
                                                    @NotNull ModifiableRootModel model)
  {
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry candidate = (LibraryOrderEntry)entry;
        if (libraryName.equals(candidate.getLibraryName())) {
          return candidate;
        }
      }
    }
    return null;
  }

  @Nullable
  public ModuleOrderEntry findIdeModuleDependency(@NotNull final ModuleDependencyData gradleDependency, @NotNull Project ideProject) {
    return findIdeModuleDependency(gradleDependency.getOwnerModule().getName(), gradleDependency.getTarget().getName(), ideProject);
  }

  @Nullable
  public ModuleOrderEntry findIdeModuleDependency(@NotNull final String ownerModuleName,
                                                  @NotNull final String dependencyModuleName,
                                                  @NotNull Project ideProject)
  {
    final Module ideOwnerModule = findIdeModule(ownerModuleName, ideProject);
    if (ideOwnerModule == null) {
      return null;
    }

    RootPolicy<ModuleOrderEntry> visitor = new RootPolicy<ModuleOrderEntry>() {
      @Override
      public ModuleOrderEntry visitModuleOrderEntry(ModuleOrderEntry ideDependency, ModuleOrderEntry value) {
        if (dependencyModuleName.equals(ideDependency.getModuleName())) {
          return ideDependency;
        }
        return value;
      }
    };
    for (OrderEntry orderEntry : myFacade.getOrderEntries(ideOwnerModule)) {
      final ModuleOrderEntry result = orderEntry.accept(visitor, null);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public ModuleOrderEntry findIdeModuleDependency(@NotNull ModuleDependencyData dependency, @NotNull ModifiableRootModel model) {
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry candidate = (ModuleOrderEntry)entry;
        if (dependency.getName().equals(candidate.getModuleName())) {
          return candidate;
        }
      }
    }
    return null;
  }
}
