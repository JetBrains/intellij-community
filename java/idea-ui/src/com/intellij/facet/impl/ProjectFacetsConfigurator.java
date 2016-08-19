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

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.facet.impl.ui.FacetEditorImpl;
import com.intellij.facet.impl.ui.FacetTreeModel;
import com.intellij.facet.impl.ui.ProjectConfigurableContext;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ProjectFacetsConfigurator implements FacetsProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectFacetsConfigurator");
  private final Map<Module, ModifiableFacetModel> myModifiableModels = new HashMap<>();
  private final Map<Facet, FacetEditorImpl> myEditors = new LinkedHashMap<>();
  private final Map<Module, FacetTreeModel> myTreeModels = new HashMap<>();
  private final Map<FacetInfo, Facet> myInfo2Facet = new HashMap<>();
  private final Map<Facet, FacetInfo> myFacet2Info = new HashMap<>();
  private final Map<Module, UserDataHolder> mySharedModuleData = new HashMap<>();
  private final Set<Facet> myFacetsToDispose = new HashSet<>();
  private final Set<Facet> myChangedFacets = new HashSet<>();
  private final Set<Facet> myCreatedFacets = new HashSet<>();
  private final StructureConfigurableContext myContext;
  private UserDataHolderBase myProjectData = new UserDataHolderBase();

  public ProjectFacetsConfigurator(final StructureConfigurableContext context, ProjectFacetsConfigurator facetsConfigurator) {
    myContext = context;

    if (facetsConfigurator != null) {
      initFrom(facetsConfigurator);
    }
  }

  private void initFrom(ProjectFacetsConfigurator facetsConfigurator) {
    myFacet2Info.putAll(facetsConfigurator.myFacet2Info);
    myInfo2Facet.putAll(facetsConfigurator.myInfo2Facet);
    myTreeModels.putAll(facetsConfigurator.myTreeModels);
    myEditors.putAll(facetsConfigurator.myEditors);
  }

  public List<Facet> removeFacet(Facet facet) {
    FacetTreeModel treeModel = getTreeModel(facet.getModule());
    FacetInfo facetInfo = myFacet2Info.get(facet);
    if (facetInfo == null) return Collections.emptyList();

    final List<Facet> removed = new ArrayList<>();
    List<FacetInfo> childrenList = treeModel.getChildren(facetInfo);
    FacetInfo[] children = childrenList.toArray(new FacetInfo[childrenList.size()]);
    for (FacetInfo child : children) {
      Facet childInfo = myInfo2Facet.get(child);
      if (childInfo != null) {
        removed.addAll(removeFacet(childInfo));
      }
    }

    treeModel.removeFacetInfo(facetInfo);
    getOrCreateModifiableModel(facet.getModule()).removeFacet(facet);
    myChangedFacets.remove(facet);
    if (myCreatedFacets.contains(facet)) {
      Disposer.dispose(facet);
    }
    final FacetEditorImpl facetEditor = myEditors.remove(facet);
    if (facetEditor != null) {
      facetEditor.disposeUIResources();
    }
    myFacet2Info.remove(facet);
    myInfo2Facet.remove(facetInfo);
    removed.add(facet);
    return removed;
  }

  public Facet createAndAddFacet(Module module, FacetType<?, ?> type, final @Nullable Facet underlying) {
    final Collection<? extends Facet> facets = getFacetsByType(module, type.getId());
    String facetName = type.getDefaultFacetName();
    int i = 2;
    while (facetExists(facetName, facets)) {
      facetName = type.getDefaultFacetName() + i;
      i++;
    }
    final Facet facet = FacetManager.getInstance(module).createFacet(type, facetName, underlying);
    myCreatedFacets.add(facet);
    addFacetInfo(facet);
    getOrCreateModifiableModel(module).addFacet(facet);
    return facet;
  }

  private boolean facetExists(final String facetName, final Collection<? extends Facet> facets) {
    for (Facet facet : facets) {
      if (getFacetName(facet).equals(facetName)) {
        return true;
      }
    }
    return false;
  }

  public void addFacetInfo(final Facet facet) {
    final FacetInfo exiting = myFacet2Info.get(facet);
    if (exiting != null) {
      LOG.assertTrue(exiting.getName().equals(facet.getName()));
      LOG.assertTrue(exiting.getFacetType().equals(facet.getType()));
      LOG.assertTrue(exiting.getConfiguration().equals(facet.getConfiguration()));
      return;
    }

    FacetInfo info = new FacetInfo(facet.getType(), facet.getName(), facet.getConfiguration(), myFacet2Info.get(facet.getUnderlyingFacet()));
    myFacet2Info.put(facet, info);
    myInfo2Facet.put(info, facet);
    getTreeModel(facet.getModule()).addFacetInfo(info);
  }

  public void addFacetInfos(final Module module) {
    final Facet[] facets = getFacetModel(module).getSortedFacets();
    for (Facet facet : facets) {
      addFacetInfo(facet);
    }
  }

  public void clearMaps() {
    myModifiableModels.clear();
    myEditors.clear();
    myTreeModels.clear();
    myInfo2Facet.clear();
    myFacet2Info.clear();
    myChangedFacets.clear();
    mySharedModuleData.clear();
  }

  private boolean isNewFacet(Facet facet) {
    final ModifiableFacetModel model = myModifiableModels.get(facet.getModule());
    return model != null && model.isNewFacet(facet);
  }

  @NotNull
  public ModifiableFacetModel getOrCreateModifiableModel(final Module module) {
    ModifiableFacetModel model = myModifiableModels.get(module);
    if (model == null) {
      model = FacetManager.getInstance(module).createModifiableModel();
      myModifiableModels.put(module, model);
    }
    return model;
  }

  @Nullable
  public FacetEditorImpl getEditor(Facet facet) {
    return myEditors.get(facet);
  }
  
  @NotNull
  public FacetEditorImpl getOrCreateEditor(Facet facet) {
    FacetEditorImpl editor = myEditors.get(facet);
    if (editor == null) {
      final Facet underlyingFacet = facet.getUnderlyingFacet();
      final FacetEditorContext parentContext = underlyingFacet != null ? getOrCreateEditor(underlyingFacet).getContext() : null;

      final FacetEditorContext context = createContext(facet, parentContext);
      editor = new FacetEditorImpl(context, facet.getConfiguration());
      editor.getComponent();
      editor.reset();
      myEditors.put(facet, editor);
    }
    return editor;
  }

  protected FacetEditorContext createContext(final @NotNull Facet facet, final @Nullable FacetEditorContext parentContext) {
    Module module = facet.getModule();
    ModulesConfigurator modulesConfigurator = myContext.getModulesConfigurator();
    ModuleEditor moduleEditor = modulesConfigurator.getModuleEditor(module);
    if (moduleEditor == null) {
      LOG.error("ModuleEditor[" + module.getName() + "]==null: disposed = " + module.isDisposed() + ", is in model = "
                + Arrays.asList(modulesConfigurator.getModules()).contains(module));
    }

    final ModuleConfigurationState state = moduleEditor.createModuleConfigurationState();
    return new MyProjectConfigurableContext(facet, parentContext, state);
  }

  private UserDataHolder getSharedModuleData(final Module module) {
    UserDataHolder dataHolder = mySharedModuleData.get(module);
    if (dataHolder == null) {
      dataHolder = new UserDataHolderBase();
      mySharedModuleData.put(module, dataHolder);
    }
    return dataHolder;
  }

  @NotNull
  public FacetModel getFacetModel(Module module) {
    final ModifiableFacetModel model = myModifiableModels.get(module);
    if (model != null) {
      return model;
    }
    return FacetManager.getInstance(module);
  }

  public void commitFacets() {
    for (ModifiableFacetModel model : myModifiableModels.values()) {
      model.commit();
    }

    for (Map.Entry<Facet, FacetEditorImpl> entry : myEditors.entrySet()) {
      entry.getValue().onFacetAdded(entry.getKey());
    }

    myModifiableModels.clear();
    for (Facet facet : myChangedFacets) {
      Module module = facet.getModule();
      if (!module.isDisposed()) {
        module.getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet);
      }
    }
    myChangedFacets.clear();
  }

  public void resetEditors() {
    for (FacetEditorImpl editor : myEditors.values()) {
      editor.reset();
    }
  }

  public void applyEditors() throws ConfigurationException {
    for (Map.Entry<Facet, FacetEditorImpl> entry : myEditors.entrySet()) {
      final FacetEditorImpl editor = entry.getValue();
      if (editor.isModified()) {
        myChangedFacets.add(entry.getKey());
      }
      editor.apply();
    }
  }

  public boolean isModified() {
    for (ModifiableFacetModel model : myModifiableModels.values()) {
      if (model.isModified()) {
        return true;
      }
    }
    for (FacetEditorImpl editor : myEditors.values()) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  public FacetTreeModel getTreeModel(Module module) {
    FacetTreeModel treeModel = myTreeModels.get(module);
    if (treeModel == null) {
      treeModel = new FacetTreeModel();
      myTreeModels.put(module, treeModel);
    }
    return treeModel;
  }

  public FacetInfo getFacetInfo(final Facet facet) {
    return myFacet2Info.get(facet);
  }

  public Facet getFacet(final FacetInfo facetInfo) {
    return myInfo2Facet.get(facetInfo);
  }

  public void disposeEditors() {
    for (Facet facet : myFacetsToDispose) {
      Disposer.dispose(facet);
    }
    myFacetsToDispose.clear();
    myCreatedFacets.clear();

    for (FacetEditorImpl editor : myEditors.values()) {
      editor.disposeUIResources();
    }
    myProjectData = null;
  }

  @NotNull
  public Facet[] getAllFacets(final Module module) {
    return getFacetModel(module).getAllFacets();
  }

  @NotNull
  public <F extends Facet> Collection<F> getFacetsByType(final Module module, final FacetTypeId<F> type) {
    return getFacetModel(module).getFacetsByType(type);
  }

  @Nullable
  public <F extends Facet> F findFacet(final Module module, final FacetTypeId<F> type, final String name) {
    return getFacetModel(module).findFacet(type, name);
  }

  private UserDataHolder getProjectData() {
    if (myProjectData == null) {
      myProjectData = new UserDataHolderBase();
    }
    return myProjectData;
  }

  public String getFacetName(Facet facet) {
    final ModifiableFacetModel model = myModifiableModels.get(facet.getModule());
    if (model != null) {
      final String newName = model.getNewName(facet);
      if (newName != null) {
        return newName;
      }
    }
    return facet.getName();
  }

  public List<Facet> removeAllFacets(final Module module) {
    List<Facet> facets = new ArrayList<>();
    FacetModel facetModel = getOrCreateModifiableModel(module);
    for (Facet facet : facetModel.getAllFacets()) {
      if (!myCreatedFacets.contains(facet)) {
        myFacetsToDispose.add(facet);
      }
      LOG.assertTrue(facet.getModule().equals(module), module + " expected but " + facet.getModule() + " found");
      facets.addAll(removeFacet(facet));
    }
    mySharedModuleData.remove(module);
    myModifiableModels.remove(module);
    return facets;
  }

  public boolean hasFacetOfType(Module module, @Nullable Facet parent, FacetTypeId<?> typeId) {
    final FacetTreeModel treeModel = getTreeModel(module);
    final FacetInfo parentInfo = getFacetInfo(parent);
    return treeModel.hasFacetOfType(parentInfo, typeId);
  }

  private class MyProjectConfigurableContext extends ProjectConfigurableContext {
    private final LibrariesContainer myContainer;

    public MyProjectConfigurableContext(final Facet facet, final FacetEditorContext parentContext, final ModuleConfigurationState state) {
      super(facet, ProjectFacetsConfigurator.this.isNewFacet(facet), parentContext, state,
            ProjectFacetsConfigurator.this.getSharedModuleData(facet.getModule()), getProjectData());
      myContainer = LibrariesContainerFactory.createContainer(myContext);
    }

    public LibrariesContainer getContainer() {
      return myContainer;
    }

  }
}
