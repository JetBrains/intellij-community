// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.ui.FacetEditorContextBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockFacetEditorContext extends FacetEditorContextBase {
  private final Project myProject;
  private boolean myNewFacet;
  private final Facet myFacet;
  private final Module myModule;

  public MockFacetEditorContext(@NotNull Facet facet) {
    this(facet.getModule(), facet);
  }

  public MockFacetEditorContext(@NotNull Module module, Facet facet) {
    this(module.getProject(), module, facet);
  }

  public MockFacetEditorContext(@NotNull Project project, Module module, Facet facet) {
    this(new DefaultModulesProvider(project), project, module, facet);
  }

  @Override
  public LibrariesContainer getContainer() {
    return LibrariesContainerFactory.createContainer(myModule);
  }

  private MockFacetEditorContext(final ModulesProvider modulesProvider, Project project, Module module, Facet facet) {
    super(facet, null, null, modulesProvider, null, null);
    myFacet = facet;
    myModule = module;
    myProject = project;
  }

  public void setNewFacet(final boolean newFacet) {
    myNewFacet = newFacet;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean isNewFacet() {
    return myNewFacet;
  }

  @Override
  @NotNull
  public Facet getFacet() {
    return myFacet;
  }

  @Override
  @NotNull
  public Module getModule() {
    return myModule;
  }

  @Override
  @Nullable
  public Facet getParentFacet() {
    return null;
  }

  @Override
  @NotNull
  public ModifiableRootModel getModifiableRootModel() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Library createProjectLibrary(final String name, final VirtualFile[] roots, final VirtualFile[] sources) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public ArtifactsStructureConfigurableContext getArtifactsStructureContext() {
    throw new UnsupportedOperationException("'getArtifactsStructureContext' not implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public ModuleRootModel getRootModel() {
    return ModuleRootManager.getInstance(myModule);
  }
}
