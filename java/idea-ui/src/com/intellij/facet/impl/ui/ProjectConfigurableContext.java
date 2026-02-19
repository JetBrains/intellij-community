// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.impl.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ProjectConfigurableContext extends FacetEditorContextBase {
  private final Module myModule;
  private final ProjectStructureConfigurable myProjectStructureConfigurable;
  private final boolean myNewFacet;
  private final ModuleConfigurationState myModuleConfigurationState;

  public ProjectConfigurableContext(final @NotNull Facet facet,
                                    final boolean isNewFacet,
                                    @Nullable FacetEditorContext parentContext,
                                    final ModuleConfigurationState state,
                                    final UserDataHolder sharedModuleData,
                                    final UserDataHolder sharedProjectData,
                                    ProjectStructureConfigurable projectStructureConfigurable) {
    super(facet, parentContext, state.getFacetsProvider(), state.getModulesProvider(), sharedModuleData, sharedProjectData);
    myModuleConfigurationState = state;
    myNewFacet = isNewFacet;
    myModule = facet.getModule();
    myProjectStructureConfigurable = projectStructureConfigurable;
  }

  @Override
  public boolean isNewFacet() {
    return myNewFacet;
  }

  @Override
  public @NotNull Project getProject() {
    return myModule.getProject();
  }

  @Override
  public @NotNull Module getModule() {
    return myModule;
  }

  @Override
  public @NotNull ModuleRootModel getRootModel() {
    return myModuleConfigurationState.getModulesProvider().getRootModel(myModule);
  }

  @Override
  public @NotNull ModifiableRootModel getModifiableRootModel() {
    return myModuleConfigurationState.getRootModel();
  }

  @Override
  public Library createProjectLibrary(final String baseName, final VirtualFile[] roots, final VirtualFile[] sources) {
    return getContainer().createLibrary(baseName, LibrariesContainer.LibraryLevel.PROJECT, roots, sources);
  }

  @Override
  public VirtualFile[] getLibraryFiles(Library library, OrderRootType rootType) {
    return getContainer().getLibraryFiles(library, rootType);
  }

  @Override
  public @NotNull ArtifactsStructureConfigurableContext getArtifactsStructureContext() {
    return myProjectStructureConfigurable.getArtifactsStructureConfigurable().getArtifactsStructureContext();
  }
}
