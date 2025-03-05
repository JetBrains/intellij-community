// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ClassMap;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public abstract class AbstractIdeModifiableModelsProvider extends IdeModelsProviderImpl implements IdeModifiableModelsProvider {
  private static final Logger LOG = Logger.getInstance(AbstractIdeModifiableModelsProvider.class);

  protected ModifiableModuleModel myModifiableModuleModel;
  private ModifiableWorkspaceModel myModifiableWorkspaceModel;
  protected final Map<Module, ModifiableRootModel> myModifiableRootModels = new HashMap<>();
  protected final Map<Module, ModifiableFacetModel> myModifiableFacetModels = new HashMap<>();
  protected final Map<Module, String> myProductionModulesForTestModules = new HashMap<>();
  protected final Map<Library, Library.ModifiableModel> myModifiableLibraryModels = new IdentityHashMap<>();
  protected final ClassMap<ModifiableModel> myModifiableModels = new ClassMap<>();
  protected final MyUserDataHolderBase myUserData;
  private volatile boolean myDisposed;

  public AbstractIdeModifiableModelsProvider(@NotNull Project project) {
    super(project);

    myUserData = new MyUserDataHolderBase();
    EP_NAME.forEachExtensionSafe(extension -> {
      Pair<Class<ModifiableModel>, ModifiableModel> pair = extension.create(project, this);
      myModifiableModels.put(pair.first, pair.second);
    });
  }

  @Override
  public @Nullable <T extends ModifiableModel> T findModifiableModel(@NotNull Class<T> instanceOf) {
    return ObjectUtils.tryCast(myModifiableModels.get(instanceOf), instanceOf);
  }

  @Override
  public @NotNull <T extends ModifiableModel> T getModifiableModel(@NotNull Class<T> instanceOf) {
    ModifiableModel model = myModifiableModels.get(instanceOf);
    if (instanceOf.isInstance(model)) {
      return instanceOf.cast(model);
    }
    throw new AssertionError(String.format("Unable to get `%s` model", instanceOf.getSimpleName()));
  }

  protected abstract ModifiableModuleModel doGetModifiableModuleModel();

  protected abstract ModifiableRootModel doGetModifiableRootModel(Module module);

  protected abstract ModifiableFacetModel doGetModifiableFacetModel(Module module);

  protected abstract Library.ModifiableModel doGetModifiableLibraryModel(Library library);

  @Override
  public abstract @NotNull LibraryTable.ModifiableModel getModifiableProjectLibrariesModel();

  @Override
  public Module @NotNull [] getModules() {
    return getModifiableModuleModel().getModules();
  }

  @Override
  public OrderEntry @NotNull [] getOrderEntries(@NotNull Module module) {
    return getRootModel(module).getOrderEntries();
  }

  @Override
  public @NotNull Module newModule(final @NotNull String filePath, final String moduleTypeId) {
    Module module = getModifiableModuleModel().newModule(filePath, moduleTypeId);
    final String moduleName = FileUtilRt.getNameWithoutExtension(new File(filePath).getName());
    if (!module.getName().equals(moduleName)) {
      try {
        getModifiableModuleModel().renameModule(module, moduleName);
      }
      catch (ModuleWithNameAlreadyExists exists) {
        LOG.warn(exists);
      }
    }

    // set module type id explicitly otherwise it can not be set if there is an existing module (with the same filePath) and w/o 'type' attribute
    module.setModuleType(moduleTypeId);
    return module;
  }

  @Override
  public @NotNull Module newModule(@NotNull ModuleData moduleData) {
    String imlName = null;
    for (String candidate: suggestModuleNameCandidates(moduleData)) {
      Module module = findIdeModule(candidate);
      if (module == null) {
        imlName = candidate;
        break;
      }
    }
    assert imlName != null : "Too many duplicated module names";

    String filePath = ExternalSystemApiUtil.toCanonicalPath(moduleData.getModuleFileDirectoryPath() + "/" + imlName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    return newModule(filePath, moduleData.getModuleTypeId());
  }

  @Override
  public @Nullable Module findIdeModule(@NotNull String ideModuleName) {
    Module module = getModifiableModuleModel().findModuleByName(ideModuleName);
    return module == null ? getModifiableModuleModel().getModuleToBeRenamed(ideModuleName) : module;
  }

  @Override
  public @Nullable Library findIdeLibrary(@NotNull LibraryData libraryData) {
    final LibraryTable.ModifiableModel libraryTable = getModifiableProjectLibrariesModel();
    for (Library ideLibrary: libraryTable.getLibraries()) {
      if (ExternalSystemApiUtil.isRelated(ideLibrary, libraryData)) return ideLibrary;
    }
    return null;
  }

  @Override
  public VirtualFile @NotNull [] getContentRoots(Module module) {
    return getRootModel(module).getContentRoots();
  }

  @Override
  public VirtualFile @NotNull [] getSourceRoots(Module module) {
    return getRootModel(module).getSourceRoots();
  }

  @Override
  public VirtualFile @NotNull [] getSourceRoots(Module module, boolean includingTests) {
    return getRootModel(module).getSourceRoots(includingTests);
  }

  @Override
  public @NotNull ModifiableModuleModel getModifiableModuleModel() {
    if (myModifiableModuleModel == null) {
      myModifiableModuleModel = doGetModifiableModuleModel();
    }
    return myModifiableModuleModel;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull ModifiableWorkspaceModel getModifiableWorkspaceModel() {
    if (myModifiableWorkspaceModel == null) {
      myModifiableWorkspaceModel = ReadAction.compute(() -> {
        var workspace = ExternalProjectsWorkspace.getInstance(myProject);
        return workspace.getModifiableModel(this);
      });
    }
    return myModifiableWorkspaceModel;
  }

  @Override
  public @NotNull ModifiableRootModel getModifiableRootModel(Module module) {
    return (ModifiableRootModel)getRootModel(module);
  }

  private @NotNull ModuleRootModel getRootModel(Module module) {
    return myModifiableRootModels.computeIfAbsent(module, k -> doGetModifiableRootModel(module));
  }

  @Override
  public @NotNull ModifiableFacetModel getModifiableFacetModel(Module module) {
    return myModifiableFacetModels.computeIfAbsent(module, k -> doGetModifiableFacetModel(module));
  }

  @Override
  public Library @NotNull [] getAllLibraries() {
    return getModifiableProjectLibrariesModel().getLibraries();
  }

  @Override
  public @Nullable Library getLibraryByName(String name) {
    return getModifiableProjectLibrariesModel().getLibraryByName(name);
  }

  @Override
  public Library createLibrary(String name) {
    return getModifiableProjectLibrariesModel().createLibrary(name);
  }

  @Override
  public Library createLibrary(String name, @Nullable ProjectModelExternalSource externalSource) {
    return getModifiableProjectLibrariesModel().createLibrary(name, null, externalSource);
  }

  @Override
  public void removeLibrary(Library library) {
    getModifiableProjectLibrariesModel().removeLibrary(library);
  }

  @Override
  public Library.ModifiableModel getModifiableLibraryModel(Library library) {
    return myModifiableLibraryModels.computeIfAbsent(library, k -> doGetModifiableLibraryModel(library));
  }

  @Override
  public String @NotNull [] getLibraryUrls(@NotNull Library library, @NotNull OrderRootType type) {
    final Library.ModifiableModel model = myModifiableLibraryModels.get(library);
    if (model != null) {
      return model.getUrls(type);
    }
    return library.getUrls(type);
  }

  @Override
  public ModalityState getModalityStateForQuestionDialogs() {
    return ModalityState.nonModal();
  }

  @Override
  public @NotNull List<Module> getAllDependentModules(@NotNull Module module) {
    final ArrayList<Module> list = new ArrayList<>();
    final Graph<Module> graph = getModuleGraph();
    for (Iterator<Module> i = graph.getOut(module); i.hasNext(); ) {
      list.add(i.next());
    }
    return list;
  }

  private Graph<Module> getModuleGraph() {
    return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<>() {
      @Override
      public @NotNull Collection<Module> getNodes() {
        return Arrays.asList(getModules());
      }

      @Override
      public @NotNull Iterator<Module> getIn(Module m) {
        Module[] dependentModules = getModifiableRootModel(m).getModuleDependencies(true);
        return Arrays.asList(dependentModules).iterator();
      }
    }));
  }

  protected static class MyUserDataHolderBase extends UserDataHolderBase {
    void clear() {
      clearUserData();
    }
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    assert !myDisposed : "Already disposed!";
    myDisposed = true;

    for (ModifiableRootModel each: myModifiableRootModels.values()) {
      if (each.isDisposed()) continue;
      each.dispose();
    }
    Disposer.dispose(getModifiableProjectLibrariesModel());

    for (Library.ModifiableModel each: myModifiableLibraryModels.values()) {
      if (each instanceof LibraryEx && ((LibraryEx)each).isDisposed()) continue;
      Disposer.dispose(each);
    }

    if (myModifiableModuleModel != null && myModifiableModuleModel.isChanged()) {
      myModifiableModuleModel.dispose();
    }

    myModifiableModels.values().forEach(ModifiableModel::dispose);
    myModifiableRootModels.clear();
    myModifiableFacetModels.clear();
    myModifiableLibraryModels.clear();
    myUserData.clear();
  }

  @Override
  public void setTestModuleProperties(Module testModule, String productionModuleName) {
    myProductionModulesForTestModules.put(testModule, productionModuleName);
  }

  @Override
  public @Nullable String getProductionModuleName(Module module) {
    return myProductionModulesForTestModules.get(module);
  }

  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
    return myUserData.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserData.putUserData(key, value);
  }
}
