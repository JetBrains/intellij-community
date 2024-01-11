// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManagerEx;
import com.intellij.openapi.roots.TestModuleProperties;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.RootConfigurationAccessor;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.EntityStorageKt;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.VersionedEntityStorage;
import com.intellij.util.containers.ClassMap;
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl;
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetManagerBridge;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.TestModulePropertiesBridge;
import com.intellij.workspaceModel.ide.legacyBridge.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class IdeModifiableModelsProviderImpl extends AbstractIdeModifiableModelsProvider {
  public static final Logger LOG = Logger.getInstance(IdeModifiableModelsProviderImpl.class);
  public static final Key<IdeModifiableModelsProviderImpl> MODIFIABLE_MODELS_PROVIDER_KEY = Key.create("IdeModelsProvider");
  private LibraryTable.ModifiableModel myLibrariesModel;
  private MutableEntityStorage diff;

  public IdeModifiableModelsProviderImpl(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public LibraryTable.ModifiableModel getModifiableProjectLibrariesModel() {
    if (myLibrariesModel != null) return myLibrariesModel;
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    return myLibrariesModel = ((ProjectLibraryTableBridge)libraryTable).getModifiableModel(getActualStorageBuilder());
  }

  @Override
  protected ModifiableModuleModel doGetModifiableModuleModel() {
    return ReadAction.compute(() -> {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      ModifiableModuleModel modifiableModel = ((ModuleManagerBridgeImpl)moduleManager).getModifiableModel(getActualStorageBuilder());
      Module[] modules = modifiableModel.getModules();
      for (Module module : modules) {
        setIdeModelsProviderForModule(module);
      }
      return modifiableModel;
    });
  }

  @Override
  @NotNull
  protected ModifiableRootModel doGetModifiableRootModel(@NotNull final Module module) {
    RootConfigurationAccessor rootConfigurationAccessor = new RootConfigurationAccessor() {
      @Nullable
      @Override
      public Library getLibrary(Library library, String libraryName, String libraryLevel) {
        if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(libraryLevel)) {
          return getModifiableProjectLibrariesModel().getLibraryByName(libraryName);
        }
        return library;
      }
    };

    return ReadAction.compute(() -> {
      ModuleRootManagerEx rootManager = ModuleRootManagerEx.getInstanceEx(module);
      return ((ModuleRootComponentBridge)rootManager).getModifiableModel(getActualStorageBuilder(), rootConfigurationAccessor);
    });
  }

  @Override
  protected ModifiableFacetModel doGetModifiableFacetModel(Module module) {
    FacetManager facetManager = FacetManager.getInstance(module);
    return ((FacetManagerBridge)facetManager).createModifiableModel(getActualStorageBuilder());
  }

  @Override
  protected Library.ModifiableModel doGetModifiableLibraryModel(Library library) {
    return ((LibraryBridge)library).getModifiableModel(getActualStorageBuilder());
  }

  @Override
  public @NotNull Module newModule(@NotNull String filePath, String moduleTypeId) {
    Module module = super.newModule(filePath, moduleTypeId);
    setIdeModelsProviderForModule(module);
    return module;
  }

  @Override
  public @NotNull Module newModule(@NotNull ModuleData moduleData) {
    Module module = super.newModule(moduleData);
    setIdeModelsProviderForModule(module);
    return module;
  }

  @Override
  public void commit() {
    LOG.trace("Applying commit for IdeaModifiableModelProvider");
    workspaceModelCommit();
  }

  private void workspaceModelCommit() {
    ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
      if (ExternalProjectsWorkspaceImpl.isDependencySubstitutionEnabled()) {
        updateSubstitutions();
      }
      LibraryTable.ModifiableModel projectLibrariesModel = getModifiableProjectLibrariesModel();
      for (Map.Entry<Library, Library.ModifiableModel> entry: myModifiableLibraryModels.entrySet()) {
        Library fromLibrary = entry.getKey();
        String libraryName = fromLibrary.getName();
        Library.ModifiableModel modifiableModel = entry.getValue();

        // Modifiable model for the new library which was disposed via ModifiableModel.removeLibrary should also be disposed
        // Modifiable model for the old library which was removed from ProjectLibraryTable should also be disposed
        if ((fromLibrary instanceof LibraryEx && ((LibraryEx)fromLibrary).isDisposed())
            || (fromLibrary.getTable() != null && libraryName != null && projectLibrariesModel.getLibraryByName(libraryName) == null)
            || (getModifiableWorkspace() != null && getModifiableWorkspace().isSubstituted(fromLibrary.getName()))) {
          Disposer.dispose(modifiableModel);
        }
        else {
          ((LibraryModifiableModelBridge)modifiableModel).prepareForCommit();
        }
      }
      ((ProjectModifiableLibraryTableBridge)projectLibrariesModel).prepareForCommit();

      ModifiableRootModel[] rootModels;
      if (myModifiableModuleModel != null) {
        Module[] modules = myModifiableModuleModel.getModules();
        for (Module module : modules) {
          module.putUserData(MODIFIABLE_MODELS_PROVIDER_KEY, null);
        }
        Set<Module> existingModules = Set.of(modules);
        rootModels = myModifiableRootModels.entrySet().stream().filter(entry -> existingModules.contains(entry.getKey())).map(Map.Entry::getValue).toArray(ModifiableRootModel[]::new);
        ((ModifiableModuleModelBridge)myModifiableModuleModel).prepareForCommit();
      }
      else {
        rootModels = myModifiableRootModels.values().toArray(new ModifiableRootModel[0]);
      }

      for (ModifiableRootModel model : rootModels) {
        assert !model.isDisposed() : "Already disposed: " + model;
      }

      for (ModifiableRootModel model : rootModels) {
        ((ModifiableRootModelBridge)model).prepareForCommit();
      }

      for (Map.Entry<Module, String> entry: myProductionModulesForTestModules.entrySet()) {
        TestModuleProperties testModuleProperties = TestModuleProperties.getInstance(entry.getKey());
        if (testModuleProperties instanceof TestModulePropertiesBridge bridge) {
          bridge.setProductionModuleNameToBuilder(entry.getValue(),
                                                  myModifiableModuleModel.getActualName(entry.getKey()),
                                                  getActualStorageBuilder());
        } else {
          testModuleProperties.setProductionModuleName(entry.getValue());
        }
      }

      for (Map.Entry<Module, ModifiableFacetModel> each: myModifiableFacetModels.entrySet()) {
        if (!each.getKey().isDisposed()) {
          ((ModifiableFacetModelBridge)each.getValue()).prepareForCommit();
        }
      }
      myModifiableModels.values().forEach(ModifiableModel::commit);
      WorkspaceModel.getInstance(myProject).updateProjectModel("External system: commit model", builder -> {
        MutableEntityStorage storageBuilder = getActualStorageBuilder();
        if (LOG.isTraceEnabled()) {
          LOG.trace("Apply builder in ModifiableModels commit. builder: " + storageBuilder);
        }
        builder.applyChangesFrom(storageBuilder);
        return null;
      });

      for (ModifiableRootModel model : rootModels) {
        ((ModifiableRootModelBridge)model).postCommit();
      }
    });
    myUserData.clear();
  }

  @Override
  public void dispose() {
    if (myModifiableModuleModel != null) {
      Module[] modules = myModifiableModuleModel.getModules();
      for (Module module : modules) {
        module.putUserData(MODIFIABLE_MODELS_PROVIDER_KEY, null);
      }
    }
    super.dispose();
  }

  public MutableEntityStorage getActualStorageBuilder() {
    if (diff != null) return diff;
    VersionedEntityStorage storage = ((WorkspaceModelImpl)WorkspaceModel.getInstance(myProject)).getEntityStorage();
    LOG.info("Ide modifiable models provider, create builder from version " + storage.getVersion());
    var initialStorage = storage.getCurrent();
    return diff = MutableEntityStorage.from(EntityStorageKt.toSnapshot(initialStorage));
  }

  private void setIdeModelsProviderForModule(@NotNull Module module) {
    module.putUserData(MODIFIABLE_MODELS_PROVIDER_KEY, this);
  }

  // temporarily open access to state for the proxy
  public ClassMap<ModifiableModel> getModifiableModels() {
    return myModifiableModels;
  }

  public Map<Library, Library.ModifiableModel> getModifiableLibraryModels() {
    return myModifiableLibraryModels;
  }

  public Map<Module, ModifiableFacetModel> getModifiableFacetModels() {
    return myModifiableFacetModels;
  }

  public void forceUpdateSubstitutions() {
    updateSubstitutions();
  }
}
