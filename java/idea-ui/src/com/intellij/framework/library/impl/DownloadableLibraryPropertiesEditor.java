/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.library.impl;

import com.intellij.facet.impl.ui.libraries.DownloadingOptionsDialog;
import com.intellij.facet.impl.ui.libraries.LibraryDownloadSettings;
import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.LibraryVersionProperties;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryPropertiesEditorBase;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorBase;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.download.DownloadableFileSetVersions;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class DownloadableLibraryPropertiesEditor extends LibraryPropertiesEditorBase<LibraryVersionProperties, DownloadableLibraryType> {
  private final DownloadableLibraryDescription myDescription;
  private final DownloadableLibraryType myLibraryType;
  private String myCurrentVersionString;

  public DownloadableLibraryPropertiesEditor(DownloadableLibraryDescription description,
                                              LibraryEditorComponent<LibraryVersionProperties> editorComponent,
                                              DownloadableLibraryType libraryType) {
    super(editorComponent, libraryType, "Change &Version...");
    myDescription = description;
    myLibraryType = libraryType;
    myCurrentVersionString = myEditorComponent.getProperties().getVersionString();
  }

  protected void edit() {
    final ModalityState current = ModalityState.current();
    myDescription.fetchVersions(new DownloadableFileSetVersions.FileSetVersionsCallback<FrameworkLibraryVersion>() {
      @Override
      public void onSuccess(@NotNull final List<? extends FrameworkLibraryVersion> versions) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
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
