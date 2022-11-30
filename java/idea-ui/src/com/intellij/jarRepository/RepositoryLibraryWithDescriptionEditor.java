/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
      properties.getExcludedDependencies(), properties.isEnableSha256Checksum(),
      RemoteRepositoriesConfiguration.getInstance(project).getRepositories(), properties.getJarRepositoryId());

    boolean isGlobalLibrary = false;
    LibraryEditor editor = myEditorComponent.getLibraryEditor();
    if (editor instanceof ExistingLibraryEditor) {
      Library library = ((ExistingLibraryEditor)editor).getLibrary();
      if (library instanceof LibraryEx) {
        LibraryEx libraryEx = (LibraryEx)library;
        LibraryTable table = libraryEx.getTable();
        isGlobalLibrary = table != null && LibraryTablesRegistrar.APPLICATION_LEVEL.equals(table.getTableLevel());
      }
    }

    RepositoryLibraryPropertiesDialog dialog = new RepositoryLibraryPropertiesDialog(
      project,
      model,
      RepositoryLibraryDescription.findDescription(properties),
      true, true);
    if (!dialog.showAndGet()) {
      return;
    }

    myEditorComponent.getProperties().changeVersion(model.getVersion());
    myEditorComponent.getProperties().setIncludeTransitiveDependencies(model.isIncludeTransitiveDependencies());
    myEditorComponent.getProperties().setExcludedDependencies(model.getExcludedDependencies());
    myEditorComponent.getProperties().setEnableSha256Checksum(model.isSha256ChecksumEnabled());
    myEditorComponent.getProperties().setJarRepositoryId(model.getRemoteRepositoryId());

    if (wasGeneratedName) {
      myEditorComponent.renameLibrary(RepositoryLibraryType.getInstance().getDescription(properties));
    }

    final LibraryEditor libraryEditor = myEditorComponent.getLibraryEditor();
    final String copyTo = getStorageRoot(myEditorComponent.getLibraryEditor().getUrls(OrderRootType.CLASSES));
    final Collection<OrderRoot> roots = JarRepositoryManager.loadDependenciesModal(
      project, properties.getRepositoryLibraryDescriptor(), model.getArtifactKinds(), null, copyTo
    );
    libraryEditor.removeAllRoots();
    if (roots != null) {
      myEditorComponent.getProperties().setArtifactsVerification(RepositoryLibraryUtils.buildRepositoryLibraryArtifactsVerification(
        properties.getRepositoryLibraryDescriptor(),
        roots)
      );

      libraryEditor.addRoots(roots);
    } else {
      properties.setArtifactsVerification(Collections.emptyList());
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
      deleteAndReloadDependencies(project, library);
      showBalloon(JavaUiBundle.message("popup.reload.success.result", library.getName()), MessageType.INFO);
    } catch (IOException | UnsupportedOperationException e) {
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
