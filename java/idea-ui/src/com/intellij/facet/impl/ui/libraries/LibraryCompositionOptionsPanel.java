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
package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.facet.ui.libraries.RemoteRepositoryInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.MutualMap;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
*/
public class LibraryCompositionOptionsPanel {
  private final MutualMap<LibrariesContainer.LibraryLevel, String> myLibraryLevels = new MutualMap<LibrariesContainer.LibraryLevel, String>(true);
  private JPanel myMainPanel;
  private JButton myAddLibraryButton;
  private JButton myAddJarsButton;
  private JCheckBox myDownloadMissingJarsCheckBox;
  private TextFieldWithBrowseButton myDirectoryField;
  private JComboBox myLibraryLevelComboBox;
  private JTextField myLibraryNameField;
  private JPanel myMissingLibrariesPanel;
  private JPanel myNewLibraryPanel;
  private JPanel myLibraryPropertiesPanel;
  private JPanel myMirrorsPanel;
  private JLabel myMissingLibrariesLabel;
  private JLabel myHiddenLabel;
  private final List<VirtualFile> myAddedJars = new ArrayList<VirtualFile>();
  private final List<Library> myUsedLibraries = new ArrayList<Library>();
  private final LibrariesContainer myLibrariesContainer;
  private final LibraryCompositionSettings myLibraryCompositionSettings;
  private final List<Library> mySuitableLibraries;
  private final LibraryDownloadingMirrorsMap myMirrorsMap;
  private List<RemoteRepositoryMirrorPanel> myMirrorPanelsList;

  public LibraryCompositionOptionsPanel(final @NotNull LibrariesContainer librariesContainer, 
                                        final @NotNull LibraryCompositionSettings libraryCompositionSettings,
                                        final @NotNull LibraryDownloadingMirrorsMap mirrorsMap) {

    myLibrariesContainer = librariesContainer;
    myLibraryCompositionSettings = libraryCompositionSettings;
    myMirrorsMap = mirrorsMap;
    addMirrorsPanels();

    myDirectoryField.addBrowseFolderListener(ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.title"),
                                             ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.description"), null,
                                             FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myUsedLibraries.addAll(myLibraryCompositionSettings.getUsedLibraries());
    myAddJarsButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        showFileChooser();
      }
    });
    myAddLibraryButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        showLibrariesChooser();
      }
    });
    myDownloadMissingJarsCheckBox.setSelected(myLibraryCompositionSettings.isDownloadLibraries());
    myDownloadMissingJarsCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateAll();
      }
    });

    mySuitableLibraries = calculateSuitableLibraries();
    myAddLibraryButton.setEnabled(!mySuitableLibraries.isEmpty());

    myLibraryLevels.put(LibrariesContainer.LibraryLevel.GLOBAL, ProjectBundle.message("combobox.item.global.library"));
    myLibraryLevels.put(LibrariesContainer.LibraryLevel.PROJECT, ProjectBundle.message("combobox.item.project.library"));
    myLibraryLevels.put(LibrariesContainer.LibraryLevel.MODULE, ProjectBundle.message("combobox.item.module.library"));
    for (String level : myLibraryLevels.getValues()) {
      myLibraryLevelComboBox.addItem(level);
    }
    myLibraryLevelComboBox.setSelectedItem(myLibraryLevels.getValue(myLibraryCompositionSettings.getLibraryLevel()));
    myLibraryNameField.setText(myLibraryCompositionSettings.getLibraryName());

    myDirectoryField.setText(FileUtil.toSystemDependentName(myLibraryCompositionSettings.getDirectoryForDownloadedLibrariesPath()));
    String jars = RequiredLibrariesInfo.getLibrariesPresentableText(myLibraryCompositionSettings.getLibraryInfos());

    myHiddenLabel.setText(UIUtil.toHtml(ProjectBundle.message("label.text.libraries.are.missing", jars)));

    updateAll();
    myMissingLibrariesPanel.getPreferredSize();
    myMainPanel.validate();
  }

  public LibraryCompositionSettings getLibraryCompositionSettings() {
    return myLibraryCompositionSettings;
  }

  private void addMirrorsPanels() {
    myMirrorsPanel.setLayout(new VerticalFlowLayout());
    myMirrorPanelsList = new ArrayList<RemoteRepositoryMirrorPanel>();
    Set<String> repositories = new HashSet<String>();
    LibraryInfo[] libraryInfos = myLibraryCompositionSettings.getLibraryInfos();
    for (LibraryInfo libraryInfo : libraryInfos) {
      LibraryDownloadInfo downloadingInfo = libraryInfo.getDownloadingInfo();
      if (downloadingInfo != null) {
        RemoteRepositoryInfo repositoryInfo = downloadingInfo.getRemoteRepository();
        if (repositoryInfo != null && repositories.add(repositoryInfo.getId())) {
          RemoteRepositoryMirrorPanel mirrorPanel = new RemoteRepositoryMirrorPanel(repositoryInfo, myMirrorsMap);
          myMirrorPanelsList.add(mirrorPanel);
          myMirrorsPanel.add(mirrorPanel.getPanel());
        }
      }
    }
  }

  private List<Library> calculateSuitableLibraries() {
    LibraryInfo[] libraryInfos = myLibraryCompositionSettings.getLibraryInfos();
    RequiredLibrariesInfo requiredLibraries = new RequiredLibrariesInfo(libraryInfos);
    List<Library> suitableLibraries = new ArrayList<Library>();
    Library[] libraries = myLibrariesContainer.getAllLibraries();
    for (Library library : libraries) {
      RequiredLibrariesInfo.RequiredClassesNotFoundInfo info =
        requiredLibraries.checkLibraries(myLibrariesContainer.getLibraryFiles(library, OrderRootType.CLASSES), false);
      if (info == null || info.getLibraryInfos().length < libraryInfos.length) {
        suitableLibraries.add(library);
      }
    }
    return suitableLibraries;
  }

  private void showLibrariesChooser() {
    ChooseLibrariesDialog dialog = new ChooseLibrariesDialog(myMainPanel, mySuitableLibraries);
    dialog.markElements(myUsedLibraries);
    dialog.show();
    if (dialog.isOK()) {
      myUsedLibraries.clear();
      myUsedLibraries.addAll(dialog.getMarkedLibraries());
      updateAll();
    }
  }

  private void showFileChooser() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, false, false, true);
    descriptor.setTitle(IdeBundle.message("file.chooser.select.paths.title"));
    descriptor.setDescription(IdeBundle.message("file.chooser.multiselect.description"));
    final VirtualFile[] files = FileChooser.chooseFiles(myAddJarsButton, descriptor, getBaseDirectory());
    ContainerUtil.addAll(myAddedJars, files);
    updateAll();
  }

  @Nullable
  private VirtualFile getBaseDirectory() {
    String path = myLibraryCompositionSettings.getBaseDirectoryForDownloadedFiles();
    VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(path);
    if (dir == null) {
      path = path.substring(0, path.lastIndexOf('/'));
      dir = LocalFileSystem.getInstance().findFileByPath(path);
    }
    return dir;
  }

  private void updateAll() {
    String missingJarsText;
    List<VirtualFile> roots = new ArrayList<VirtualFile>();
    roots.addAll(myAddedJars);
    for (Library library : myUsedLibraries) {
      ContainerUtil.addAll(roots, myLibrariesContainer.getLibraryFiles(library, OrderRootType.CLASSES));
    }
    RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = new RequiredLibrariesInfo(myLibraryCompositionSettings.getLibraryInfos()).checkLibraries(
      VfsUtil.toVirtualFileArray(roots), false);
    if (info != null) {
      missingJarsText = ProjectBundle.message("label.text.libraries.are.missing", info.getMissingJarsText());
    }
    else {
      missingJarsText = ProjectBundle.message("label.text.all.library.files.found");
    }
    myMissingLibrariesLabel.setText(UIUtil.toHtml(missingJarsText));
    ((CardLayout)myMissingLibrariesPanel.getLayout()).show(myMissingLibrariesPanel, "shown");
    if (info == null) {
      myDownloadMissingJarsCheckBox.setSelected(false);
    }


    myNewLibraryPanel.setVisible(info != null || !myAddedJars.isEmpty());
    myLibraryPropertiesPanel.setVisible(!myAddedJars.isEmpty() || myDownloadMissingJarsCheckBox.isSelected());
    myDownloadMissingJarsCheckBox.setEnabled(info != null);
    myDirectoryField.setEnabled(myDownloadMissingJarsCheckBox.isSelected());
    UIUtil.setEnabled(myMirrorsPanel, myDownloadMissingJarsCheckBox.isSelected(), true);
  }

  public void updateRepositoriesMirrors(final LibraryDownloadingMirrorsMap mirrorsMap) {
    for (RemoteRepositoryMirrorPanel mirrorPanel : myMirrorPanelsList) {
      mirrorPanel.updateComboBox(mirrorsMap);
    }
  }

  public void saveSelectedRepositoriesMirrors(final LibraryDownloadingMirrorsMap mirrorsMap) {
    for (RemoteRepositoryMirrorPanel mirrorPanel : myMirrorPanelsList) {
      mirrorsMap.setMirror(mirrorPanel.getRemoteRepository(), mirrorPanel.getSelectedMirror());
    }
  }

  public void apply() {
    saveSelectedRepositoriesMirrors(myMirrorsMap);
    if (myDownloadMissingJarsCheckBox.isSelected()) {
      myLibraryCompositionSettings.setDownloadLibraries(true);
      myLibraryCompositionSettings.setDirectoryForDownloadedLibrariesPath(FileUtil.toSystemIndependentName(myDirectoryField.getText()));
    }
    else {
      myLibraryCompositionSettings.setDownloadLibraries(false);
    }
    myLibraryCompositionSettings.setUsedLibraries(myUsedLibraries);
    myLibraryCompositionSettings.setLibraryLevel(myLibraryLevels.getKey((String)myLibraryLevelComboBox.getSelectedItem()));
    myLibraryCompositionSettings.setLibraryName(myLibraryNameField.getText());
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
