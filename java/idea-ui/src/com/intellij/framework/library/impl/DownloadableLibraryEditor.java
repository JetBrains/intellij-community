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
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorBase;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author nik
 */
public class DownloadableLibraryEditor extends LibraryPropertiesEditor {
  private JPanel myMainPanel;
  private JLabel myDescriptionLabel;
  private JButton myChangeVersionButton;
  private boolean myModified;
  private final DownloadableLibraryDescription myDescription;
  private final LibraryEditorComponent<LibraryVersionProperties> myEditorComponent;
  private final DownloadableLibraryType myLibraryType;
  private String myCurrentVersionString;

  public DownloadableLibraryEditor(final DownloadableLibraryDescription description,
                                   final LibraryEditorComponent<LibraryVersionProperties> editorComponent,
                                   DownloadableLibraryType libraryType) {
    myDescription = description;
    myEditorComponent = editorComponent;
    myLibraryType = libraryType;
    updateDescription();
    myCurrentVersionString = myEditorComponent.getProperties().getVersionString();
    myChangeVersionButton.setVisible(!myEditorComponent.isNewLibrary());
    myChangeVersionButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        changeVersion();
      }
    });
  }

  private void updateDescription() {
    myDescriptionLabel.setText(myLibraryType.getDescription(myEditorComponent.getProperties()));
  }

  private void changeVersion() {
    final ModalityState current = ModalityState.current();
    myDescription.fetchLibraryVersions(new DownloadableLibraryDescription.LibraryVersionsCallback() {
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
            final LibraryDownloadSettings settings = DownloadingOptionsDialog.showDialog(myMainPanel, initialSettings, versions, false);
            if (settings != null) {
              final NewLibraryEditor editor = settings.download(myMainPanel);
              if (editor != null) {
                myEditorComponent.getLibraryEditor().removeAllRoots();
                myEditorComponent.getLibraryEditor().setName(editor.getName());
                editor.applyTo((LibraryEditorBase)myEditorComponent.getLibraryEditor());
                myEditorComponent.updateRootsTree();
                myCurrentVersionString = settings.getVersion().getVersionString();
                updateDescription();
                myModified = true;
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

  @NotNull
  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public void apply() {
    myEditorComponent.getProperties().setVersionString(myCurrentVersionString);
  }

  @Override
  public boolean isModified() {
    return myModified;
  }

  @Override
  public void reset() {
    updateDescription();
  }
}
