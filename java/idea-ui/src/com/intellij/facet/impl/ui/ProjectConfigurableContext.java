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

package com.intellij.facet.impl.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
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

/**
 * @author nik
 */
public abstract class ProjectConfigurableContext extends FacetEditorContextBase {
  private final Module myModule;
  private final boolean myNewFacet;
  private final ModuleConfigurationState myModuleConfigurationState;

  public ProjectConfigurableContext(final @NotNull Facet facet, final boolean isNewFacet,
                                    @Nullable FacetEditorContext parentContext,
                                    final ModuleConfigurationState state, final UserDataHolder sharedModuleData,
                                    final UserDataHolder sharedProjectData) {
    super(facet, parentContext, state.getFacetsProvider(), state.getModulesProvider(), sharedModuleData, sharedProjectData);
    myModuleConfigurationState = state;
    myNewFacet = isNewFacet;
    myModule = facet.getModule();
  }

  @Nullable
  public ModuleBuilder getModuleBuilder() {
    return null;
  }

  public boolean isNewFacet() {
    return myNewFacet;
  }

  @NotNull
  public Project getProject() {
    return myModule.getProject();
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  @Override
  public ModuleRootModel getRootModel() {
    return myModuleConfigurationState.getModulesProvider().getRootModel(myModule);
  }

  @NotNull
  public ModifiableRootModel getModifiableRootModel() {
    return myModuleConfigurationState.getRootModel();
  }

  @Nullable
  public WizardContext getWizardContext() {
    return null;
  }

  public Library createProjectLibrary(final String baseName, final VirtualFile[] roots, final VirtualFile[] sources) {
    return getContainer().createLibrary(baseName, LibrariesContainer.LibraryLevel.PROJECT, roots, sources);
  }

  public VirtualFile[] getLibraryFiles(Library library, OrderRootType rootType) {
    return getContainer().getLibraryFiles(library, rootType);
  }

  @NotNull
  @Override
  public ArtifactsStructureConfigurableContext getArtifactsStructureContext() {
    return ProjectStructureConfigurable.getInstance(getProject()).getArtifactsStructureConfigurable().getArtifactsStructureContext();
  }
}
