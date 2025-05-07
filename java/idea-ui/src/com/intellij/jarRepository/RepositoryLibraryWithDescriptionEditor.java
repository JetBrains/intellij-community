// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository;

import com.intellij.ide.JavaUiBundle;
import com.intellij.jarRepository.settings.RepositoryLibraryPropertiesDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryPropertiesEditorBase;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactKind;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import static org.jetbrains.idea.maven.utils.library.RepositoryUtils.*;

public class RepositoryLibraryWithDescriptionEditor
  extends LibraryPropertiesEditorBase<RepositoryLibraryProperties, RepositoryLibraryType> {

  public RepositoryLibraryWithDescriptionEditor(LibraryEditorComponent<RepositoryLibraryProperties> editorComponent) {
    super(editorComponent, RepositoryLibraryType.getInstance(), null);
    setupReloadButton();
  }

  @Override
  public void apply() {
  }

  @Override
  protected void edit() {
    @NotNull RepositoryLibraryProperties properties = myEditorComponent.getProperties();
    //String oldVersion = properties.getVersion();
    boolean wasGeneratedName =
      RepositoryLibraryType.getInstance().getDescription(properties).equals(myEditorComponent.getLibraryEditor().getName());
    final EnumSet<ArtifactKind> artifactKinds = ArtifactKind.kindsOf(libraryHasSources(myEditorComponent.getLibraryEditor()),
                                                                     libraryHasJavaDocs(myEditorComponent.getLibraryEditor()),
                                                                     properties.getPackaging());
    if (libraryHasExternalAnnotations(myEditorComponent.getLibraryEditor())) {
      artifactKinds.add(ArtifactKind.ANNOTATIONS);
    }

    final Project project = myEditorComponent.getProject();
    assert project != null : "EditorComponent's project must not be null in order to be used with RepositoryLibraryWithDescriptionEditor";

    RepositoryLibraryPropertiesModel model = new RepositoryLibraryPropertiesModel(
      properties.getVersion(),
      artifactKinds, properties.isIncludeTransitiveDependencies(),
      properties.getExcludedDependencies(),
      RemoteRepositoriesConfiguration.getInstance(project).getRepositories(), properties.getJarRepositoryId());

    boolean isGlobalLibrary = false;
    LibraryEditor editor = myEditorComponent.getLibraryEditor();
    if (editor instanceof ExistingLibraryEditor libraryEditor && libraryEditor.getLibrary() instanceof LibraryEx libraryEx) {
      LibraryTable table = libraryEx.getTable();
      isGlobalLibrary = table != null && LibraryTablesRegistrar.APPLICATION_LEVEL.equals(table.getTableLevel());
    }

    RepositoryLibraryPropertiesDialog dialog = new RepositoryLibraryPropertiesDialog(
      project,
      model,
      RepositoryLibraryDescription.findDescription(properties),
      true, true, isGlobalLibrary);
    if (!dialog.showAndGet()) {
      return;
    }

    myEditorComponent.getProperties().changeVersion(model.getVersion());
    myEditorComponent.getProperties().setIncludeTransitiveDependencies(model.isIncludeTransitiveDependencies());
    myEditorComponent.getProperties().setExcludedDependencies(model.getExcludedDependencies());
    myEditorComponent.getProperties().setJarRepositoryId(model.getRemoteRepositoryId());

    if (wasGeneratedName) {
      myEditorComponent.renameLibrary(RepositoryLibraryType.getInstance().getDescription(properties));
    }

    final LibraryEditor libraryEditor = myEditorComponent.getLibraryEditor();
    final String copyTo = getStorageRoot(project, myEditorComponent.getLibraryEditor().getUrls(OrderRootType.CLASSES));
    final Collection<OrderRoot> roots = JarRepositoryManager.loadDependenciesModal(
      project, properties.getRepositoryLibraryDescriptor(), model.getArtifactKinds(), null, copyTo
    );

    if (roots == null || RepositoryLibraryUtils.isVerifiableRootsChanged(libraryEditor, roots)) {
      /* Reset verification if verifiable roots changed */
      /* If auto-rebuild enabled, RepositoryLibraryChangeListener will handle the change and build verification for new roots */
      myEditorComponent.getProperties().setArtifactsVerification(Collections.emptyList());
    }

    libraryEditor.removeAllRoots();
    if (roots != null) {
      libraryEditor.addRoots(roots);
    }
    myEditorComponent.updateRootsTree();
    updateDescription();
  }

  private void setupReloadButton() {
    Project project = myEditorComponent.getProject();
    VirtualFile directory = myEditorComponent.getExistingRootDirectory();
    if (myEditorComponent.isNewLibrary() || project == null || directory == null) return;

    LibraryEditor editor = myEditorComponent.getLibraryEditor();
    if (!(editor instanceof ExistingLibraryEditor)) return;

    Library library = ((ExistingLibraryEditor)editor).getLibrary();
    if (!(library instanceof LibraryEx)) return;

    String toolTipText = JavaUiBundle.message("button.reload.description", directory.getPath());
    myReloadButton.setVisible(true);
    myReloadButton.setToolTipText(toolTipText);
    myReloadButton.addActionListener(e -> reloadLibraryDirectory(project, (LibraryEx)library));
  }

  private void reloadLibraryDirectory(Project project, LibraryEx library) {
    try {
      deleteAndReloadDependencies(project, library).onError(e -> {
        showBalloon(JavaUiBundle.message("popup.reload.failed.result", library.getName()), MessageType.ERROR);
      }).onSuccess(roots -> {
        showBalloon(JavaUiBundle.message("popup.reload.success.result", library.getName()), MessageType.INFO);
      });
    }
    catch (IOException | UnsupportedOperationException e) {
      var error = e.getLocalizedMessage();
      showBalloon(error, MessageType.ERROR);
    }
  }

  private void showBalloon(@NlsSafe String text, MessageType type) {
    JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(text, type, null)
      .setHideOnClickOutside(true)
      .createBalloon()
      .show(RelativePoint.getSouthWestOf(myReloadButton.getRootPane()), Balloon.Position.atRight);
  }
}
