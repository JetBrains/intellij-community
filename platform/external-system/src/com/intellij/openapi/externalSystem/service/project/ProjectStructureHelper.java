package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.id.*;
import com.intellij.openapi.externalSystem.service.project.change.ProjectStructureChangesModel;
import com.intellij.openapi.externalSystem.util.ArtifactInfo;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BooleanFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.LIBRARY_DEPENDENCY;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE_DEPENDENCY;

/**
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 2/6/12 3:28 PM
 */
public class ProjectStructureHelper {

  @NotNull private final ProjectStructureChangesModel  myChangesModel;
  @NotNull private final PlatformFacade                myFacade;
  @NotNull private final ExternalLibraryPathTypeMapper myLibraryPathTypeMapper;

  public ProjectStructureHelper(@NotNull ProjectStructureChangesModel model,
                                @NotNull PlatformFacade facade,
                                @NotNull ExternalLibraryPathTypeMapper mapper)
  {
    myChangesModel = model;
    myFacade = facade;
    myLibraryPathTypeMapper = mapper;
  }

  /**
   * Allows to answer if target library dependency is still available at the target project.
   *
   * @param id          target library id
   * @param ideProject  target project
   * @return            <code>true</code> if target library dependency is still available at the target project;
   *                    <code>false</code> otherwise
   */
  public boolean isIdeLibraryDependencyExist(@NotNull final LibraryDependencyId id, @NotNull Project ideProject) {
    return findIdeLibraryDependency(id.getOwnerModuleName(), id.getDependencyName(), ideProject) != null;
  }

  /**
   * Allows to answer if target library dependency is still available at the target gradle project.
   *
   * @param id                target library id
   * @param externalSystemId  target external system id
   * @param ideProject        target ide project
   * @return                  <code>true</code> if target library dependency is still available at the target external system;
   *                          <code>false</code> otherwise
   */
  public boolean isExternalLibraryDependencyExist(@NotNull final LibraryDependencyId id,
                                                  @NotNull ProjectSystemId externalSystemId,
                                                  @NotNull Project ideProject)
  {
    return findExternalLibraryDependency(id.getOwnerModuleName(), id.getDependencyName(), externalSystemId, ideProject) != null;
  }

  public boolean isIdeModuleDependencyExist(@NotNull final ModuleDependencyId id, @NotNull Project ideProject) {
    return findIdeModuleDependency(id.getOwnerModuleName(), id.getDependencyName(), ideProject) != null;
  }

  public boolean isExternalModuleDependencyExist(@NotNull final ModuleDependencyId id, @NotNull Project ideProject) {
    return findIdeModuleDependency(id.getOwnerModuleName(), id.getDependencyName(), ideProject) != null;
  }

  @Nullable
  public Module findIdeModule(@NotNull ModuleData module, @NotNull Project ideProject) {
    return findIdeModule(module.getName(), ideProject);
  }

  @Nullable
  public Object findModule(@NotNull String moduleName, @NotNull ProjectSystemId owner, @NotNull Project ideProject) {
    if (ProjectSystemId.IDE.equals(owner)) {
      return findIdeModule(moduleName, ideProject);
    }
    else {
      return findExternalModule(moduleName, owner, ideProject);
    }
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
  public DataNode<ModuleData> findExternalModule(@NotNull String name,
                                                 @NotNull ProjectSystemId externalSystemId,
                                                 @NotNull Project ideProject)
  {
    final DataNode<ProjectData> project = myChangesModel.getExternalProject(externalSystemId, ideProject);
    if (project == null) {
      return null;
    }
    for (DataNode<ModuleData> moduleNode : ExternalSystemUtil.getChildren(project, ProjectKeys.MODULE)) {
      if (name.equals(moduleNode.getData().getName())) {
        return moduleNode;
      }
    }
    return null;
  }

  @Nullable
  public DataNode<ContentRootData> findExternalContentRoot(@NotNull ContentRootId id,
                                                           @NotNull ProjectSystemId externalSystemId,
                                                           @NotNull Project ideProject)
  {
    final DataNode<ModuleData> moduleNode = findExternalModule(id.getModuleName(), externalSystemId, ideProject);
    if (moduleNode == null) {
      return null;
    }
    for (DataNode<ContentRootData> contentRootNode : ExternalSystemUtil.getChildren(moduleNode, ProjectKeys.CONTENT_ROOT)) {
      if (id.getRootPath().equals(contentRootNode.getData().getRootPath())) {
        return contentRootNode;
      }
    }
    return null;
  }

  @Nullable
  public Object findContentRoot(@NotNull ContentRootId id, @NotNull ProjectSystemId owner, @NotNull Project ideProject) {
    if (ProjectSystemId.IDE.equals(owner)) {
      return findIdeContentRoot(id, ideProject);
    }
    else {
      return findExternalContentRoot(id, owner, ideProject);
    }
  }

  @Nullable
  public ModuleAwareContentRoot findIdeContentRoot(@NotNull ContentRootId id, @NotNull Project ideProject) {
    final Module module = findIdeModule(id.getModuleName(), ideProject);
    if (module == null) {
      return null;
    }
    for (ModuleAwareContentRoot contentRoot : myFacade.getContentRoots(module)) {
      final VirtualFile file = contentRoot.getFile();
      if (id.getRootPath().equals(file.getPath())) {
        return contentRoot;
      }
    }
    return null;
  }

  @Nullable
  public Object findLibrary(@NotNull String libraryName, @NotNull ProjectSystemId owner, @NotNull Project ideProject) {
    if (ProjectSystemId.IDE.equals(owner)) {
      return findIdeLibrary(libraryName, ideProject);
    }
    else {
      return findExternalLibrary(libraryName, owner, ideProject);
    }
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
      ArtifactInfo info = ExternalSystemUtil.parseArtifactInfo(ExternalSystemUtil.getLibraryName(library));
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
      if (libraryName.equals(ExternalSystemUtil.getLibraryName(ideLibrary))) {
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
      if (jarPath.equals(ExternalSystemUtil.getLocalFileSystemPath(file))) {
        return library;
      }
    }
    return null;
  }

  @Nullable
  public LibraryOrderEntry findIdeLibraryDependency(@NotNull LibraryDependencyId id, @NotNull Project ideProject) {
    return findIdeLibraryDependency(id.getOwnerModuleName(), id.getLibraryId().getLibraryName(), ideProject);
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
        if (ideDependency instanceof ModuleLibraryOrderEntryImpl && libraryName.equals(ExternalSystemUtil.getLibraryName(library))) {
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
  public DataNode<LibraryData> findExternalLibrary(@NotNull final LibraryId id,
                                                   @NotNull ProjectSystemId externalSystemId,
                                                   @NotNull Project ideProject)
  {
    return findExternalLibrary(id.getLibraryName(), externalSystemId, ideProject);
  }

  @Nullable
  public DataNode<LibraryData> findExternalLibrary(@NotNull final String libraryName,
                                                   @NotNull ProjectSystemId externalSystemId,
                                                   @NotNull Project ideProject)
  {
    final DataNode<ProjectData> project = myChangesModel.getExternalProject(externalSystemId, ideProject);
    if (project == null) {
      return null;
    }
    for (DataNode<LibraryData> libraryNode : ExternalSystemUtil.getChildren(project, ProjectKeys.LIBRARY)) {
      if (libraryName.equals(libraryNode.getData().getName())) {
        return libraryNode;
      }
    }
    return null;
  }

  @Nullable
  public DataNode<LibraryData> findExternalLibrary(@NotNull String libraryName,
                                                   @NotNull LibraryPathType jarType,
                                                   @NotNull String jarPath,
                                                   @NotNull ProjectSystemId externalSystemId,
                                                   @NotNull Project ideProject)
  {
    DataNode<LibraryData> libraryNode = findExternalLibrary(libraryName, externalSystemId, ideProject);
    if (libraryNode == null) {
      return null;
    }
    return libraryNode.getData().getPaths(jarType).contains(jarPath) ? libraryNode : null;
  }

  @Nullable
  public Object findLibraryDependency(@NotNull final String moduleName,
                                      @NotNull final String libraryName,
                                      @NotNull ProjectSystemId owner,
                                      @NotNull Project ideProject) {
    if (ProjectSystemId.IDE.equals(owner)) {
      return findIdeLibraryDependency(moduleName, libraryName, ideProject);
    }
    else {
      return findExternalLibraryDependency(moduleName, libraryName, owner, ideProject);
    }
  }

  @Nullable
  public DataNode<LibraryDependencyData> findExternalLibraryDependency(@NotNull LibraryDependencyId id,
                                                                       @NotNull ProjectSystemId externalSystemId,
                                                                       @NotNull Project ideProject)
  {
    return findExternalLibraryDependency(id.getOwnerModuleName(), id.getLibraryId().getLibraryName(), externalSystemId, ideProject);
  }

  @Nullable
  public DataNode<LibraryDependencyData> findExternalLibraryDependency(@NotNull final String moduleName,
                                                                       @NotNull final String libraryName,
                                                                       @NotNull ProjectSystemId externalSystemId,
                                                                       @NotNull Project ideProject)
  {
    final DataNode<ModuleData> module = findExternalModule(moduleName, externalSystemId, ideProject);
    if (module == null) {
      return null;
    }
    return ExternalSystemUtil.find(module, LIBRARY_DEPENDENCY, new BooleanFunction<DataNode<LibraryDependencyData>>() {
      @Override
      public boolean fun(DataNode<LibraryDependencyData> node) {
        return libraryName.equals(node.getData().getName());
      }
    });
  }

  @Nullable
  public DataNode<ModuleDependencyData> findExternalModuleDependency(@NotNull ModuleDependencyId id,
                                                                     @NotNull ProjectSystemId externalSystemId,
                                                                     @NotNull Project ideProject)
  {
    return findExternalModuleDependency(id.getOwnerModuleName(), id.getDependencyName(), externalSystemId, ideProject);
  }

  @Nullable
  public DataNode<ModuleDependencyData> findExternalModuleDependency(@NotNull final String ownerModuleName,
                                                                     @NotNull final String dependencyModuleName,
                                                                     @NotNull ProjectSystemId externalSystemId,
                                                                     @NotNull Project ideProject)
  {
    final DataNode<ModuleData> ownerModule = findExternalModule(ownerModuleName, externalSystemId, ideProject);
    if (ownerModule == null) {
      return null;
    }
    return ExternalSystemUtil.find(ownerModule, MODULE_DEPENDENCY, new BooleanFunction<DataNode<ModuleDependencyData>>() {
      @Override
      public boolean fun(DataNode<ModuleDependencyData> node) {
        return dependencyModuleName.equals(node.getData().getName());
      }
    });
  }

  @Nullable
  public ModuleOrderEntry findIdeModuleDependency(@NotNull final ModuleDependencyId id, @NotNull Project ideProject) {
    return findIdeModuleDependency(id.getOwnerModuleName(), id.getDependencyName(), ideProject);
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

  @Nullable
  public Object findModuleDependency(@NotNull final String ownerModuleName,
                                     @NotNull final String dependencyModuleName,
                                     @NotNull ProjectSystemId owner,
                                     @NotNull Project ideProject) {
    if (ProjectSystemId.IDE.equals(owner)) {
      return findIdeModuleDependency(ownerModuleName, dependencyModuleName, ideProject);
    }
    else {
      return findExternalModuleDependency(ownerModuleName, dependencyModuleName, owner, ideProject);
    }
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

  @Nullable
  public JarData findIdeJar(@NotNull JarId jarId, @NotNull Project ideProject) {
    Library library = findIdeLibrary(jarId.getLibraryId().getLibraryName(), ideProject);
    if (library == null) {
      return null;
    }
    for (VirtualFile file : library.getFiles(myLibraryPathTypeMapper.map(jarId.getLibraryPathType()))) {
      if (jarId.getPath().equals(ExternalSystemUtil.getLocalFileSystemPath(file))) {
        return new JarData(jarId.getPath(), jarId.getLibraryPathType(), library, null, ProjectSystemId.IDE);
      }
    }
    return null;
  }
}
