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

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetManagerBridge;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge;
import com.intellij.workspaceModel.ide.legacyBridge.*;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class IdeModifiableModelsProviderImpl extends AbstractIdeModifiableModelsProvider {
  private LibraryTable.ModifiableModel myLibrariesModel;
  private WorkspaceEntityStorage initialStorage;
  private WorkspaceEntityStorageBuilder diff;

  public IdeModifiableModelsProviderImpl(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public LibraryTable.ModifiableModel getModifiableProjectLibrariesModel() {
    if (myLibrariesModel != null) return myLibrariesModel;
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    if (WorkspaceModel.isEnabled()) {
      return myLibrariesModel = ((ProjectLibraryTableBridge)libraryTable).getModifiableModel(getActualStorageBuilder());
    }
    return myLibrariesModel = libraryTable.getModifiableModel();
  }

  @Override
  protected ModifiableModuleModel doGetModifiableModuleModel() {
    return ReadAction.compute(() -> {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      if (WorkspaceModel.isEnabled()) {
        return ((ModuleManagerComponentBridge)moduleManager).getModifiableModel(getActualStorageBuilder());
      }
      return moduleManager.getModifiableModel();
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
      if (WorkspaceModel.isEnabled()) {
        return ((ModuleRootComponentBridge)rootManager).getModifiableModel(getActualStorageBuilder(), initialStorage, rootConfigurationAccessor);
      }
      return rootManager.getModifiableModel(rootConfigurationAccessor);
    });
  }

  @Override
  protected ModifiableFacetModel doGetModifiableFacetModel(Module module) {
    FacetManager facetManager = FacetManager.getInstance(module);
    if (WorkspaceModel.isEnabled()) {
      return ((FacetManagerBridge)facetManager).createModifiableModel(getActualStorageBuilder());
    }
    return facetManager.createModifiableModel();
  }

  @Override
  protected Library.ModifiableModel doGetModifiableLibraryModel(Library library) {
    if (WorkspaceModel.isEnabled()) {
      return ((LibraryBridge)library).getModifiableModel(getActualStorageBuilder());
    }
    return library.getModifiableModel();
  }

  @Override
  public void commit() {
    if (WorkspaceModel.isEnabled()) {
      workspaceModelCommit();
    } else {
      super.commit();
    }
  }

  private void workspaceModelCommit() {
    ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
      if (ExternalProjectsWorkspaceImpl.isDependencySubstitutionEnabled()) {
        updateSubstitutions();
      }
      for (Map.Entry<Library, Library.ModifiableModel> entry: myModifiableLibraryModels.entrySet()) {
        Library fromLibrary = entry.getKey();
        Library.ModifiableModel modifiableModel = entry.getValue();
        // removed and (previously) not committed library is being disposed by LibraryTableBase.LibraryModel.removeLibrary
        // the modifiable model of such library shouldn't be committed
        if (fromLibrary instanceof LibraryEx && ((LibraryEx)fromLibrary).isDisposed()) {
          Disposer.dispose(modifiableModel);
        }
        else {
          ((LibraryModifiableModelBridge)modifiableModel).prepareForCommit();
        }
      }
      ((ProjectModifiableLibraryTableBridge)getModifiableProjectLibrariesModel()).prepareForCommit();

      Collection<ModifiableRootModel> rootModels = myModifiableRootModels.values();
      ModifiableRootModel[] rootModels1 = rootModels.toArray(new ModifiableRootModel[0]);
      for (ModifiableRootModel model: rootModels1) {
        assert !model.isDisposed() : "Already disposed: " + model;
      }
      if (myModifiableModuleModel != null) ((ModifiableModuleModelBridge)myModifiableModuleModel).prepareForCommit();
      for (ModifiableRootModel model : rootModels1) {
        ((ModifiableRootModelBridge)model).prepareForCommit();
      }

      for (Map.Entry<Module, String> entry: myProductionModulesForTestModules.entrySet()) {
        TestModuleProperties.getInstance(entry.getKey()).setProductionModuleName(entry.getValue());
      }

      for (Map.Entry<Module, ModifiableFacetModel> each: myModifiableFacetModels.entrySet()) {
        if (!each.getKey().isDisposed()) {
          ((ModifiableFacetModelBridge)each.getValue()).prepareForCommit();
        }
      }
      myModifiableModels.values().forEach(ModifiableModel::commit);
      WorkspaceModel.getInstance(myProject).updateProjectModel(builder -> {
        builder.addDiff(getActualStorageBuilder());
        return null;
      });
    });
    myUserData.clear();
  }

  private WorkspaceEntityStorageBuilder getActualStorageBuilder() {
    if (diff != null) return diff;
    initialStorage = WorkspaceModel.getInstance(myProject).getEntityStorage().getCurrent();
    return diff = WorkspaceEntityStorageBuilder.Companion.from(initialStorage);
  }
}
