package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.packaging.ChooseLibrariesDialog;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseModulesDialog;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.module.Module;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.facet.Facet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Collections;

/**
 * @author nik
 */
public class ArtifactEditorContextImpl implements ArtifactEditorContext {
  private final ArtifactsStructureConfigurableContext myParent;
  private final ArtifactEditorEx myEditor;
  private ArtifactValidationManagerImpl myValidationManager;

  public ArtifactEditorContextImpl(ArtifactsStructureConfigurableContext parent, ArtifactEditorEx editor) {
    myParent = parent;
    myEditor = editor;
  }

  @NotNull
  public ModifiableArtifactModel getModifiableArtifactModel() {
    return myParent.getModifiableArtifactModel();
  }

  @NotNull
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

  public void ensureRootIsWritable(@NotNull Artifact artifact) {
    myParent.ensureRootIsWritable(artifact);
  }

  public ArtifactEditor getOrCreateEditor(Artifact artifact) {
    return myParent.getOrCreateEditor(artifact);
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
    ProjectStructureConfigurable.getInstance(getProject()).selectProjectOrGlobalLibrary(library, true);
  }

  public List<Artifact> chooseArtifacts(final List<? extends Artifact> artifacts, final String title) {
    ChooseArtifactsDialog dialog = new ChooseArtifactsDialog(getProject(), artifacts, title, "");
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

  public void queueValidation() {
    myEditor.queueValidation();
  }

  @NotNull
  public ArtifactType getArtifactType() {
    return myEditor.getArtifact().getArtifactType();
  }

  public ArtifactValidationManagerImpl getValidationManager() {
    return myValidationManager;
  }

  public void setValidationManager(ArtifactValidationManagerImpl validationManager) {
    myValidationManager = validationManager;
  }

  public List<Module> chooseModules(final List<Module> modules, final String title) {
    ChooseModulesDialog dialog = new ChooseModulesDialog(getProject(), modules, title, "");
    dialog.show();
    List<Module> selected = dialog.getChosenElements();
    return dialog.isOK() ? selected : Collections.<Module>emptyList();
  }

  public List<Library> chooseLibraries(final List<Library> libraries, final String title) {
    ChooseLibrariesDialog dialog = new ChooseLibrariesDialog(getProject(), libraries, title, "");
    dialog.show();
    return dialog.isOK() ? dialog.getChosenElements() : Collections.<Library>emptyList();
  }

}
