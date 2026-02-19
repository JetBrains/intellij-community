// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.library.impl;

import com.intellij.facet.impl.ui.libraries.DownloadingOptionsDialog;
import com.intellij.facet.impl.ui.libraries.LibraryDownloadSettings;
import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.LibraryVersionProperties;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorBase;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryPropertiesEditorBase;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.download.DownloadableFileSetVersions;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DownloadableLibraryPropertiesEditor extends LibraryPropertiesEditorBase<LibraryVersionProperties, DownloadableLibraryType> {
  private final DownloadableLibraryDescription myDescription;
  private String myCurrentVersionString;

  public DownloadableLibraryPropertiesEditor(DownloadableLibraryDescription description,
                                              LibraryEditorComponent<LibraryVersionProperties> editorComponent,
                                              DownloadableLibraryType libraryType) {
    super(editorComponent, libraryType, JavaUiBundle.message("downloadable.library.properties.change.version.title"));
    myDescription = description;
    myCurrentVersionString = myEditorComponent.getProperties().getVersionString();
  }

  @Override
  protected void edit() {
    final ModalityState current = ModalityState.current();
    myDescription.fetchVersions(new DownloadableFileSetVersions.FileSetVersionsCallback<>() {
      @Override
      public void onSuccess(final @NotNull List<? extends FrameworkLibraryVersion> versions) {
        ApplicationManager.getApplication().invokeLater(() -> {
          String pathForDownloaded = "";
          final VirtualFile existingRootDirectory = myEditorComponent.getExistingRootDirectory();
          if (existingRootDirectory != null) {
            pathForDownloaded = existingRootDirectory.getPath();
          }
          else {
            final VirtualFile baseDir = myEditorComponent.getBaseDirectory();
            if (baseDir != null) {
              pathForDownloaded = baseDir.getPath() + "/lib";
            }
          }
          final LibraryDownloadSettings initialSettings = new LibraryDownloadSettings(getCurrentVersion(versions), myLibraryType,
                                                                                      LibrariesContainer.LibraryLevel.PROJECT,
                                                                                      pathForDownloaded);
          final LibraryDownloadSettings settings = DownloadingOptionsDialog.showDialog(getMainPanel(), initialSettings, versions, false);
          if (settings != null) {
            final NewLibraryEditor editor = settings.download(getMainPanel(), null);
            if (editor != null) {
              final LibraryEditorBase target = (LibraryEditorBase)myEditorComponent.getLibraryEditor();
              target.removeAllRoots();
              myEditorComponent.renameLibrary(editor.getName());
              target.setType(myLibraryType);
              editor.applyTo(target);
              myEditorComponent.updateRootsTree();
              myCurrentVersionString = settings.getVersion().getVersionString();
              setModified();
            }
          }
        }, current);
      }
    });
  }

  private FrameworkLibraryVersion getCurrentVersion(List<? extends FrameworkLibraryVersion> versions) {
    for (FrameworkLibraryVersion version : versions) {
      if (version.getVersionString().equals(myCurrentVersionString)) {
        return version;
      }
    }
    return versions.get(0);
  }

  @Override
  public void apply() {
    myEditorComponent.getProperties().setVersionString(myCurrentVersionString);
  }
}
