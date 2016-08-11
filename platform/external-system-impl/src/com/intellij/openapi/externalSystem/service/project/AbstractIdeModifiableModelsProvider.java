/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.ManifestFileProvider;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.DefaultManifestFileProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isRelated;

public abstract class AbstractIdeModifiableModelsProvider extends IdeModelsProviderImpl implements IdeModifiableModelsProvider {
  private static final Logger LOG = Logger.getInstance(AbstractIdeModifiableModelsProvider.class);

  private ModifiableModuleModel myModifiableModuleModel;
  private Map<Module, ModifiableRootModel> myModifiableRootModels = new THashMap<>();
  private Map<Module, ModifiableFacetModel> myModifiableFacetModels = new THashMap<>();
  private Map<Module, String> myProductionModulesForTestModules = new THashMap<>();
  private Map<Library, Library.ModifiableModel> myModifiableLibraryModels = new IdentityHashMap<>();
  private ModifiableArtifactModel myModifiableArtifactModel;
  private AbstractIdeModifiableModelsProvider.MyPackagingElementResolvingContext myPackagingElementResolvingContext;
  private final ArtifactExternalDependenciesImporter myArtifactExternalDependenciesImporter;

  public AbstractIdeModifiableModelsProvider(@NotNull Project project) {
    super(project);
    myArtifactExternalDependenciesImporter = new ArtifactExternalDependenciesImporterImpl();
  }

  protected abstract ModifiableArtifactModel doGetModifiableArtifactModel();

  protected abstract ModifiableModuleModel doGetModifiableModuleModel();

  protected abstract ModifiableRootModel doGetModifiableRootModel(Module module);

  protected abstract ModifiableFacetModel doGetModifiableFacetModel(Module module);

  protected abstract Library.ModifiableModel doGetModifiableLibraryModel(Library library);

  @NotNull
  @Override
  public abstract LibraryTable.ModifiableModel getModifiableProjectLibrariesModel();

  @NotNull
  @Override
  public Module[] getModules() {
    return getModifiableModuleModel().getModules();
  }

  protected void processExternalArtifactDependencies() {
    myArtifactExternalDependenciesImporter.applyChanges(getModifiableArtifactModel(), getPackagingElementResolvingContext());
  }

  @Override
  public PackagingElementResolvingContext getPackagingElementResolvingContext() {
    if (myPackagingElementResolvingContext == null) {
      myPackagingElementResolvingContext = new MyPackagingElementResolvingContext();
    }
    return myPackagingElementResolvingContext;
  }

  @NotNull
  @Override
  public OrderEntry[] getOrderEntries(@NotNull Module module) {
    return getRootModel(module).getOrderEntries();
  }

  @NotNull
  @Override
  public Module newModule(@NotNull final String filePath, final String moduleTypeId) {
    Module module = getModifiableModuleModel().newModule(filePath, moduleTypeId);
    final String moduleName = FileUtil.getNameWithoutExtension(new File(filePath));
    if (!module.getName().equals(moduleName)) {
      try {
        getModifiableModuleModel().renameModule(module, moduleName);
      }
      catch (ModuleWithNameAlreadyExists exists) {
        LOG.warn(exists);
      }
    }

    // set module type id explicitly otherwise it can not be set if there is an existing module (with the same filePath) and w/o 'type' attribute
    module.setOption(Module.ELEMENT_TYPE, moduleTypeId);
    return module;
  }

  @Nullable
  @Override
  public Module findIdeModule(@NotNull String ideModuleName) {
    Module module = getModifiableModuleModel().findModuleByName(ideModuleName);
    return module == null ? getModifiableModuleModel().getModuleToBeRenamed(ideModuleName) : module;
  }

  @Nullable
  @Override
  public Library findIdeLibrary(@NotNull LibraryData libraryData) {
    final LibraryTable.ModifiableModel libraryTable = getModifiableProjectLibrariesModel();
    for (Library ideLibrary : libraryTable.getLibraries()) {
      if (isRelated(ideLibrary, libraryData)) return ideLibrary;
    }
    return null;
  }

  @Override
  @NotNull
  public VirtualFile[] getContentRoots(Module module) {
    return getRootModel(module).getContentRoots();
  }

  @NotNull
  @Override
  public VirtualFile[] getSourceRoots(Module module) {
    return getRootModel(module).getSourceRoots();
  }

  @NotNull
  @Override
  public VirtualFile[] getSourceRoots(Module module, boolean includingTests) {
    return getRootModel(module).getSourceRoots(includingTests);
  }

  @NotNull
  @Override
  public ModifiableModuleModel getModifiableModuleModel() {
    if (myModifiableModuleModel == null) {
      myModifiableModuleModel = doGetModifiableModuleModel();
    }
    return myModifiableModuleModel;
  }

  @Override
  @NotNull
  public ModifiableRootModel getModifiableRootModel(Module module) {
    return (ModifiableRootModel)getRootModel(module);
  }

  @NotNull
  private ModuleRootModel getRootModel(Module module) {
    ModifiableRootModel result = myModifiableRootModels.get(module);
    if (result == null) {
      result = doGetModifiableRootModel(module);
      myModifiableRootModels.put(module, result);
    }
    return result;
  }

  @Override
  @NotNull
  public ModifiableFacetModel getModifiableFacetModel(Module module) {
    ModifiableFacetModel result = myModifiableFacetModels.get(module);
    if (result == null) {
      result = doGetModifiableFacetModel(module);
      myModifiableFacetModels.put(module, result);
    }
    return result;
  }

  @Override
  @NotNull
  public ModifiableArtifactModel getModifiableArtifactModel() {
    if (myModifiableArtifactModel == null) {
      myModifiableArtifactModel = doGetModifiableArtifactModel();
    }
    return myModifiableArtifactModel;
  }

  @Override
  @NotNull
  public Library[] getAllLibraries() {
    return getModifiableProjectLibrariesModel().getLibraries();
  }

  @Override
  @Nullable
  public Library getLibraryByName(String name) {
    return getModifiableProjectLibrariesModel().getLibraryByName(name);
  }

  @Override
  public Library createLibrary(String name) {
    return getModifiableProjectLibrariesModel().createLibrary(name);
  }

  @Override
  public void removeLibrary(Library library) {
    getModifiableProjectLibrariesModel().removeLibrary(library);
  }

  @Override
  public Library.ModifiableModel getModifiableLibraryModel(Library library) {
    Library.ModifiableModel result = myModifiableLibraryModels.get(library);
    if (result == null) {
      result = doGetModifiableLibraryModel(library);
      myModifiableLibraryModels.put(library, result);
    }
    return result;
  }

  @NotNull
  @Override
  public String[] getLibraryUrls(@NotNull Library library, @NotNull OrderRootType type) {
    final Library.ModifiableModel model = myModifiableLibraryModels.get(library);
    if (model != null) {
      return model.getUrls(type);
    }
    return library.getUrls(type);
  }

  @Override
  public ModalityState getModalityStateForQuestionDialogs() {
    return ModalityState.NON_MODAL;
  }

  @Override
  public ArtifactExternalDependenciesImporter getArtifactExternalDependenciesImporter() {
    return myArtifactExternalDependenciesImporter;
  }

  @NotNull
  @Override
  public List<Module> getAllDependentModules(@NotNull Module module) {
    final ArrayList<Module> list = new ArrayList<>();
    final Graph<Module> graph = getModuleGraph(true);
    for (Iterator<Module> i = graph.getOut(module); i.hasNext();) {
      list.add(i.next());
    }
    return list;
  }

  private Graph<Module> getModuleGraph(final boolean includeTests) {
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Module>() {
      @Override
      public Collection<Module> getNodes() {
        return ContainerUtil.list(getModules());
      }

      @Override
      public Iterator<Module> getIn(Module m) {
        Module[] dependentModules = getModifiableRootModel(m).getModuleDependencies(includeTests);
        return Arrays.asList(dependentModules).iterator();
      }
    }));
  }

  private class MyPackagingElementResolvingContext implements PackagingElementResolvingContext {
    private final ModulesProvider myModulesProvider = new MyModulesProvider();
    private final MyFacetsProvider myFacetsProvider = new MyFacetsProvider();
    private final ManifestFileProvider myManifestFileProvider = new DefaultManifestFileProvider(this);

    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public ArtifactModel getArtifactModel() {
      return AbstractIdeModifiableModelsProvider.this.getModifiableArtifactModel();
    }

    @NotNull
    public ModulesProvider getModulesProvider() {
      return myModulesProvider;
    }

    @NotNull
    public FacetsProvider getFacetsProvider() {
      return myFacetsProvider;
    }

    public Library findLibrary(@NotNull String level, @NotNull String libraryName) {
      if (level.equals(LibraryTablesRegistrar.PROJECT_LEVEL)) {
        return getLibraryByName(libraryName);
      }
      final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, myProject);
      return table != null ? table.getLibraryByName(libraryName) : null;
    }

    @NotNull
    @Override
    public ManifestFileProvider getManifestFileProvider() {
      return myManifestFileProvider;
    }
  }

  private class MyModulesProvider implements ModulesProvider {
    @NotNull
    public Module[] getModules() {
      return AbstractIdeModifiableModelsProvider.this.getModules();
    }

    public Module getModule(String name) {
      return AbstractIdeModifiableModelsProvider.this.findIdeModule(name);
    }

    public ModuleRootModel getRootModel(@NotNull Module module) {
      return AbstractIdeModifiableModelsProvider.this.getModifiableRootModel(module);
    }

    public FacetModel getFacetModel(@NotNull Module module) {
      return AbstractIdeModifiableModelsProvider.this.getModifiableFacetModel(module);
    }
  }

  private class MyFacetsProvider implements FacetsProvider {
    @NotNull
    public Facet[] getAllFacets(Module module) {
      return getModifiableFacetModel(module).getAllFacets();
    }

    @NotNull
    public <F extends Facet> Collection<F> getFacetsByType(Module module, FacetTypeId<F> type) {
      return getModifiableFacetModel(module).getFacetsByType(type);
    }

    public <F extends Facet> F findFacet(Module module, FacetTypeId<F> type, String name) {
      return getModifiableFacetModel(module).findFacet(type, name);
    }
  }

  @Override
  public void commit() {
    ((ProjectRootManagerEx)ProjectRootManager.getInstance(myProject)).mergeRootsChangesDuring(() -> {
      processExternalArtifactDependencies();
      for (Library.ModifiableModel each : myModifiableLibraryModels.values()) {
        each.commit();
      }
      getModifiableProjectLibrariesModel().commit();

      Collection<ModifiableRootModel> rootModels = myModifiableRootModels.values();
      ModifiableRootModel[] rootModels1 = rootModels.toArray(new ModifiableRootModel[rootModels.size()]);
      for (ModifiableRootModel model : rootModels1) {
        assert !model.isDisposed() : "Already disposed: " + model;
      }

      if (myModifiableModuleModel != null) {
        ModifiableModelCommitter.multiCommit(rootModels1, myModifiableModuleModel);
      } else {
        for (ModifiableRootModel model : rootModels1) {
          model.commit();
        }
      }
      for (Map.Entry<Module, String> entry : myProductionModulesForTestModules.entrySet()) {
        TestModuleProperties.getInstance(entry.getKey()).setProductionModuleName(entry.getValue());
      }

      for (Map.Entry<Module, ModifiableFacetModel> each : myModifiableFacetModels.entrySet()) {
        if(!each.getKey().isDisposed()) {
          each.getValue().commit();
        }
      }
      if (myModifiableArtifactModel != null) {
        myModifiableArtifactModel.commit();
      }
    });
  }

  @Override
  public void dispose() {
    for (ModifiableRootModel each : myModifiableRootModels.values()) {
      if (each.isDisposed()) continue;
      each.dispose();
    }
    Disposer.dispose(getModifiableProjectLibrariesModel());

    for (Library.ModifiableModel each : myModifiableLibraryModels.values()) {
      Disposer.dispose(each);
    }

    if(myModifiableModuleModel != null) {
      myModifiableModuleModel.dispose();
    }
    if (myModifiableArtifactModel != null) {
      myModifiableArtifactModel.dispose();
    }

    myModifiableRootModels.clear();
    myModifiableFacetModels.clear();
    myModifiableLibraryModels.clear();
  }

  @Override
  public void setTestModuleProperties(Module testModule, String productionModuleName) {
    myProductionModulesForTestModules.put(testModule, productionModuleName);
  }
}
