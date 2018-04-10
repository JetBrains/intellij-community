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

import com.intellij.jarRepository.settings.RepositoryLibraryPropertiesDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryPropertiesEditorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.idea.maven.utils.library.RepositoryUtils;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

import java.util.Collection;

public class RepositoryLibraryWithDescriptionEditor
  extends LibraryPropertiesEditorBase<RepositoryLibraryProperties, RepositoryLibraryType> {

  public RepositoryLibraryWithDescriptionEditor(LibraryEditorComponent<RepositoryLibraryProperties> editorComponent) {
    super(editorComponent, RepositoryLibraryType.getInstance(), null);
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
    RepositoryLibraryPropertiesModel model = new RepositoryLibraryPropertiesModel(
      properties.getVersion(),
      RepositoryUtils.libraryHasSources(myEditorComponent.getLibraryEditor()),
      RepositoryUtils.libraryHasJavaDocs(myEditorComponent.getLibraryEditor()), properties.isIncludeTransitiveDependencies());

    final Project project = myEditorComponent.getProject();
    assert project != null : "EditorComponent's project must not be null in order to be used with RepositoryLibraryWithDescriptionEditor";

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
    if (wasGeneratedName) {
      myEditorComponent.renameLibrary(RepositoryLibraryType.getInstance().getDescription(properties));
    }
    final LibraryEditor libraryEditor = myEditorComponent.getLibraryEditor();
    final String copyTo = RepositoryUtils.getStorageRoot(myEditorComponent.getLibraryEditor().getUrls(OrderRootType.CLASSES), project);
    final Collection<OrderRoot> roots = JarRepositoryManager.loadDependenciesModal(
      project, properties, model.isDownloadSources(), model.isDownloadJavaDocs(), copyTo, null
    );
    libraryEditor.removeAllRoots();
    if (roots != null) {
      libraryEditor.addRoots(roots);
    }
    myEditorComponent.updateRootsTree();
    updateDescription();
  }
}
