// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetInfo;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.impl.ui.FacetEditorFacade;
import com.intellij.facet.impl.ui.FacetTreeModel;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class MockFacetEditorFacade implements FacetEditorFacade {
  private FacetInfo mySelectedFacet;
  private final FacetTreeModel myModel = new FacetTreeModel();

  public void setSelectedFacet(final FacetInfo selectedFacet) {
    mySelectedFacet = selectedFacet;
  }

  public FacetTreeModel getModel() {
    return myModel;
  }

  @Override
  public boolean nodeHasFacetOfType(final @Nullable FacetInfo facet, FacetTypeId typeId) {
    return myModel.hasFacetOfType(facet, typeId);
  }

  @Override
  @Nullable
  public FacetInfo getSelectedFacetInfo() {
    return mySelectedFacet;
  }

  @Override
  @Nullable
  public ModuleType getSelectedModuleType() {
    return JavaModuleType.getModuleType();
  }

  @Override
  public Facet createFacet(final FacetInfo parent, final FacetType type) {
    return null;
  }

  @Override
  public Collection<FacetInfo> getFacetsByType(final FacetType<?, ?> type) {
    return myModel.getFacetInfos(type);
  }

  @Override
  @Nullable
  public FacetInfo getParent(final FacetInfo facet) {
    return myModel.getParent(facet);
  }

  @Override
  public ProjectStructureConfigurable getProjectStructureConfigurable() {
    throw new UnsupportedOperationException();
  }
}
