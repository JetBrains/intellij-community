// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.*;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.FacetEditorFacade;
import com.intellij.facet.impl.ui.FacetTreeModel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.*;

public class FacetEditorFacadeImpl implements FacetEditorFacade {
  private static final Logger LOG = Logger.getInstance(FacetEditorFacadeImpl.class);
  private final ProjectStructureConfigurable myStructureConfigurable;
  private final Runnable myTreeUpdater;
  private final Map<Facet, MasterDetailsComponent.MyNode> myNodes = new HashMap<>();
  private final Map<Facet, FacetConfigurable> myConfigurables = new HashMap<>();

  public FacetEditorFacadeImpl(final ProjectStructureConfigurable structureConfigurable, final Runnable treeUpdater) {
    myStructureConfigurable = structureConfigurable;
    myTreeUpdater = treeUpdater;
  }

  public boolean addFacetsNodes(final Module module, final MasterDetailsComponent.MyNode moduleNode) {
    boolean facetsExist = false;

    getFacetConfigurator().addFacetInfos(module);

    final FacetModel facetModel = getFacetConfigurator().getFacetModel(module);
    for (Facet facet : facetModel.getSortedFacets()) {
      addFacetNode(facet, moduleNode);
      facetsExist = true;
    }

    return facetsExist;
  }

  private void addFacetNode(Facet facet) {
    MasterDetailsComponent.MyNode moduleNode = getModuleStructureConfigurable().findModuleNode(facet.getModule());
    if (moduleNode == null) return;
    addFacetNode(facet, moduleNode);
    FacetStructureConfigurable facetStructureConfigurable = myStructureConfigurable.getFacetStructureConfigurable();
    final MasterDetailsComponent.MyNode facetTypeNode = facetStructureConfigurable.getOrCreateFacetTypeNode(facet.getType());
    LOG.assertTrue(facetTypeNode != null, "Cannot found node for " + facet.getType());
    facetStructureConfigurable.addFacetNodes(facetTypeNode, Collections.singletonList(facet), this);
  }

  private MasterDetailsComponent.MyNode addFacetNode(final Facet facet, final MasterDetailsComponent.MyNode moduleNode) {
    final MasterDetailsComponent.MyNode existing = findFacetNode(facet, moduleNode);
    if (existing != null) return existing;

    final FacetConfigurable facetConfigurable = getOrCreateConfigurable(facet);
    final MasterDetailsComponent.MyNode facetNode = new MasterDetailsComponent.MyNode(facetConfigurable);
    myNodes.put(facet, facetNode);
    MasterDetailsComponent.MyNode parent = moduleNode;
    final Facet underlyingFacet = facet.getUnderlyingFacet();
    if (underlyingFacet != null) {
      parent = myNodes.get(underlyingFacet);
      LOG.assertTrue(parent != null);
    }
    getModuleStructureConfigurable().addNode(facetNode, parent);
    return facetNode;
  }

  public FacetConfigurable getOrCreateConfigurable(final Facet facet) {
    FacetConfigurable configurable = myConfigurables.get(facet);
    if (configurable == null) {
      configurable = new FacetConfigurable(myConfigurables, facet, myStructureConfigurable.getContext(), myTreeUpdater);
      myConfigurables.put(facet, configurable);
    }
    return configurable;
  }

  private ModuleStructureConfigurable getModuleStructureConfigurable() {
    return myStructureConfigurable.getModulesConfig();
  }

  @Nullable
  private static MasterDetailsComponent.MyNode findFacetNode(final Facet facet, final MasterDetailsComponent.MyNode moduleNode) {
    for (int i = 0; i < moduleNode.getChildCount(); i++) {
      final TreeNode node = moduleNode.getChildAt(i);
      if (node instanceof MasterDetailsComponent.MyNode configNode &&
          configNode.getConfigurable() instanceof FacetConfigurable configurable) {
        final Facet<?> existingFacet = configurable.getEditableObject();
        if (existingFacet != null && existingFacet.equals(facet)) {
          return configNode;
        }
      }
    }

    return null;
  }

  @Override
  public boolean nodeHasFacetOfType(final FacetInfo facet, FacetTypeId typeId) {
    final Module selectedModule = getSelectedModule();
    if (selectedModule == null) {
      return false;
    }
    final FacetTreeModel facetTreeModel = getFacetConfigurator().getTreeModel(selectedModule);
    return facetTreeModel.hasFacetOfType(facet, typeId);
  }

  @Override
  public Facet createFacet(final FacetInfo parent, FacetType type) {
    return createAndAddFacet(type, getSelectedModule(), getFacetConfigurator().getFacet(parent));
  }

  public Facet createAndAddFacet(FacetType type, Module module, final Facet underlying) {
    final Facet facet = getFacetConfigurator().createAndAddFacet(module, type, underlying);
    addFacetNode(facet);
    return facet;
  }

  @Override
  public Collection<FacetInfo> getFacetsByType(final FacetType<?,?> type) {
    final Module selectedModule = getSelectedModule();
    if (selectedModule == null) return Collections.emptyList();
    final FacetModel facetModel = getFacetConfigurator().getFacetModel(selectedModule);
    final Collection<? extends Facet> facets = facetModel.getFacetsByType(type.getId());

    final ArrayList<FacetInfo> infos = new ArrayList<>();
    for (Facet facet : facets) {
      final FacetInfo facetInfo = getFacetConfigurator().getFacetInfo(facet);
      if (facetInfo != null) {
        infos.add(facetInfo);
      }
    }
    return infos;
  }

  @Override
  @Nullable
  public FacetInfo getParent(final FacetInfo facetInfo) {
    final Module module = getFacetConfigurator().getFacet(facetInfo).getModule();
    return getFacetConfigurator().getTreeModel(module).getParent(facetInfo);
  }

  private ProjectFacetsConfigurator getFacetConfigurator() {
    return getModuleStructureConfigurable().getFacetConfigurator();
  }

  @Nullable
  private Facet getSelectedFacet() {
    final Object selectedObject = getModuleStructureConfigurable().getSelectedObject();
    if (selectedObject instanceof Facet) {
      return (Facet)selectedObject;
    }
    return null;
  }

  @Nullable
  private Module getSelectedModule() {
    final Object selected = getModuleStructureConfigurable().getSelectedObject();
    if (selected instanceof Module) {
      return (Module)selected;
    }
    if (selected instanceof Facet) {
      return ((Facet<?>)selected).getModule();
    }
    return null;
  }

  @Override
  @Nullable
  public ModuleType getSelectedModuleType() {
    final Module module = getSelectedModule();
    return module != null ? ModuleType.get(module) : null;
  }

  @Override
  @Nullable
  public FacetInfo getSelectedFacetInfo() {
    final Facet facet = getSelectedFacet();
    return facet != null ? getFacetConfigurator().getFacetInfo(facet) : null;
  }

  public void clearMaps(boolean clearNodes) {
    myConfigurables.clear();
    if (clearNodes) {
      myNodes.clear();
    }
  }

  @Override
  public ProjectStructureConfigurable getProjectStructureConfigurable() {
    return myStructureConfigurable;
  }
}
