// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.ManifestFileProvider;
import com.intellij.packaging.impl.ui.ChooseArtifactsDialog;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.util.ui.classpath.ChooseLibrariesFromTablesDialog;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ArtifactEditorContextImpl implements ArtifactEditorContext {
  private final ArtifactsStructureConfigurableContext myParent;
  private final ArtifactEditorEx myEditor;

  public ArtifactEditorContextImpl(ArtifactsStructureConfigurableContext parent, ArtifactEditorEx editor) {
    myParent = parent;
    myEditor = editor;
  }

  @Override
  public @NotNull ModifiableArtifactModel getOrCreateModifiableArtifactModel() {
    return myParent.getOrCreateModifiableArtifactModel();
  }

  @Override
  public ModifiableModuleModel getModifiableModuleModel() {
    return myParent.getModifiableModuleModel();
  }

  @Override
  public @NotNull ModifiableRootModel getOrCreateModifiableRootModel(@NotNull Module module) {
    return myParent.getOrCreateModifiableRootModel(module);
  }

  @Override
  public ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType) {
    return myParent.getManifestFile(element, artifactType);
  }

  @Override
  public @NotNull Project getProject() {
    return myParent.getProject();
  }

  @Override
  public CompositePackagingElement<?> getRootElement(@NotNull Artifact artifact) {
    return myParent.getRootElement(artifact);
  }

  @Override
  public void editLayout(@NotNull Artifact artifact, Runnable runnable) {
    myParent.editLayout(artifact, runnable);
  }

  @Override
  public ArtifactEditor getOrCreateEditor(Artifact artifact) {
    return myParent.getOrCreateEditor(artifact);
  }

  @Override
  public ArtifactEditor getThisArtifactEditor() {
    return myEditor;
  }

  @Override
  public void selectArtifact(@NotNull Artifact artifact) {
    myParent.getProjectStructureConfigurable().select(artifact, true);
  }

  @Override
  public void selectFacet(@NotNull Facet<?> facet) {
    myParent.getProjectStructureConfigurable().select(facet, true);
  }

  @Override
  public void selectModule(@NotNull Module module) {
    myParent.getProjectStructureConfigurable().select(module.getName(), null, true);
  }

  @Override
  public void selectLibrary(@NotNull Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      myParent.getProjectStructureConfigurable().selectProjectOrGlobalLibrary(library, true);
    }
    else {
      final Module module = ((LibraryEx)library).getModule();
      if (module != null) {
        final ModuleRootModel rootModel = myParent.getModulesProvider().getRootModel(module);
        final String libraryName = library.getName();
        for (OrderEntry entry : rootModel.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry libraryEntry && OrderEntryUtil.isModuleLibraryOrderEntry(entry)) {
            if (libraryName != null && libraryName.equals(libraryEntry.getLibraryName())
               || libraryName == null && library.equals(libraryEntry.getLibrary())) {
              myParent.getProjectStructureConfigurable().selectOrderEntry(module, libraryEntry);
              return;
            }
          }
        }
      }
    }
  }

  @Override
  public List<Artifact> chooseArtifacts(final List<? extends Artifact> artifacts, final @NlsContexts.DialogTitle String title) {
    ChooseArtifactsDialog dialog = new ChooseArtifactsDialog(getProject(), artifacts, title, null);
    return dialog.showAndGet() ? dialog.getChosenElements() : Collections.emptyList();
  }


  @Override
  public @NotNull ArtifactModel getArtifactModel() {
    return myParent.getArtifactModel();
  }

  @Override
  public @NotNull ModulesProvider getModulesProvider() {
    return myParent.getModulesProvider();
  }

  @Override
  public @NotNull FacetsProvider getFacetsProvider() {
    return myParent.getFacetsProvider();
  }

  @Override
  public Library findLibrary(@NotNull String level, @NotNull String libraryName) {
    return myParent.findLibrary(level, libraryName);
  }

  @Override
  public @NotNull ManifestFileProvider getManifestFileProvider() {
    return myParent.getManifestFileProvider();
  }

  @Override
  public void queueValidation() {
    myParent.queueValidation(getArtifact());
  }

  @Override
  public @NotNull ArtifactType getArtifactType() {
    return myEditor.getArtifact().getArtifactType();
  }

  @Override
  public List<Module> chooseModules(final List<? extends Module> modules, final @NlsContexts.DialogTitle String title) {
    return new ChooseModulesDialog(getProject(), modules, title, null).showAndGetResult();
  }

  @Override
  public List<Library> chooseLibraries(final @NlsContexts.DialogTitle String title) {
    final ChooseLibrariesFromTablesDialog dialog = ChooseLibrariesFromTablesDialog.createDialog(title, getProject(), false);
    return dialog.showAndGet() ? dialog.getSelectedLibraries() : Collections.emptyList();
  }

  @Override
  public Artifact getArtifact() {
    return myEditor.getArtifact();
  }

  public ArtifactsStructureConfigurableContext getParent() {
    return myParent;
  }
}
