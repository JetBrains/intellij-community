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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.impl.ui.ChooseArtifactsDialog;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.util.ui.classpath.ChooseLibrariesFromTablesDialog;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactEditorContextImpl implements ArtifactEditorContext {
  private final ArtifactsStructureConfigurableContext myParent;
  private final ArtifactEditorEx myEditor;

  public ArtifactEditorContextImpl(ArtifactsStructureConfigurableContext parent, ArtifactEditorEx editor) {
    myParent = parent;
    myEditor = editor;
  }

  @NotNull
  public ModifiableArtifactModel getOrCreateModifiableArtifactModel() {
    return myParent.getOrCreateModifiableArtifactModel();
  }

  public ModifiableModuleModel getModifiableModuleModel() {
    return myParent.getModifiableModuleModel();
  }

  @NotNull
  public ModifiableRootModel getOrCreateModifiableRootModel(@NotNull Module module) {
    return myParent.getOrCreateModifiableRootModel(module);
  }

  public ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType) {
    return myParent.getManifestFile(element, artifactType);
  }

  @NotNull
  public Project getProject() {
    return myParent.getProject();
  }

  public CompositePackagingElement<?> getRootElement(@NotNull Artifact artifact) {
    return myParent.getRootElement(artifact);
  }

  public void editLayout(@NotNull Artifact artifact, Runnable runnable) {
    myParent.editLayout(artifact, runnable);
  }

  public ArtifactEditor getOrCreateEditor(Artifact artifact) {
    return myParent.getOrCreateEditor(artifact);
  }

  public ArtifactEditor getThisArtifactEditor() {
    return myEditor;
  }

  public void selectArtifact(@NotNull Artifact artifact) {
    ProjectStructureConfigurable.getInstance(getProject()).select(artifact, true);
  }

  public void selectFacet(@NotNull Facet<?> facet) {
    ProjectStructureConfigurable.getInstance(getProject()).select(facet, true);
  }

  public void selectModule(@NotNull Module module) {
    ProjectStructureConfigurable.getInstance(getProject()).select(module.getName(), null, true);
  }

  public void selectLibrary(@NotNull Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      ProjectStructureConfigurable.getInstance(getProject()).selectProjectOrGlobalLibrary(library, true);
    }
    else {
      final Module module = ((LibraryImpl)library).getModule();
      if (module != null) {
        final ModuleRootModel rootModel = myParent.getModulesProvider().getRootModel(module);
        final String libraryName = library.getName();
        for (OrderEntry entry : rootModel.getOrderEntries()) {
          if (entry instanceof ModuleLibraryOrderEntryImpl) {
            final ModuleLibraryOrderEntryImpl libraryEntry = (ModuleLibraryOrderEntryImpl)entry;
            if (libraryName != null && libraryName.equals(libraryEntry.getLibraryName())
               || libraryName == null && library.equals(libraryEntry.getLibrary())) {
              ModuleStructureConfigurable.getInstance(getProject()).selectOrderEntry(module, libraryEntry);
              return;
            }
          }
        }
      }
    }
  }

  public List<Artifact> chooseArtifacts(final List<? extends Artifact> artifacts, final String title) {
    ChooseArtifactsDialog dialog = new ChooseArtifactsDialog(getProject(), artifacts, title, null);
    dialog.show();
    return dialog.isOK() ? dialog.getChosenElements() : Collections.<Artifact>emptyList();
  }


  @NotNull
  public ArtifactModel getArtifactModel() {
    return myParent.getArtifactModel();
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return myParent.getModulesProvider();
  }

  @NotNull
  public FacetsProvider getFacetsProvider() {
    return myParent.getFacetsProvider();
  }

  public Library findLibrary(@NotNull String level, @NotNull String libraryName) {
    return myParent.findLibrary(level, libraryName);
  }

  public void queueValidation() {
    myParent.queueValidation(getArtifact());
  }

  @NotNull
  public ArtifactType getArtifactType() {
    return myEditor.getArtifact().getArtifactType();
  }

  public List<Module> chooseModules(final List<Module> modules, final String title) {
    ChooseModulesDialog dialog = new ChooseModulesDialog(getProject(), modules, title, null);
    dialog.show();
    List<Module> selected = dialog.getChosenElements();
    return dialog.isOK() ? selected : Collections.<Module>emptyList();
  }

  public List<Library> chooseLibraries(final String title) {
    final ChooseLibrariesFromTablesDialog dialog = ChooseLibrariesFromTablesDialog.createDialog(title, getProject(), false);
    dialog.show();
    return dialog.isOK() ? dialog.getSelectedLibraries() : Collections.<Library>emptyList();
  }

  public Artifact getArtifact() {
    return myEditor.getArtifact();
  }

  public ArtifactsStructureConfigurableContext getParent() {
    return myParent;
  }
}
