/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public class DownloadingOptionsDialog extends DialogWrapper {

  private JPanel myPanel;
  private CheckBoxList myFilesList;
  private TextFieldWithBrowseButton myDirectoryField;
  private JTextField myLibraryNameField;
  private JComboBox myLevelComboBox;
  private JCheckBox myDownloadSourcesCheckBox;
  private JLabel myFilesToDownloadLabel;
  private JLabel myCopyDownloadedFilesToLabel;
  private JCheckBox myDownloadJavadocsCheckBox;
  private final LibraryCompositionSettings mySettings;

  protected DownloadingOptionsDialog(Component parent, LibraryCompositionSettings settings) {
    super(parent, true);
    setTitle("Downloading Options");
    mySettings = settings;

    myFilesList.setModel(new CollectionListModel(ContainerUtil.map2Array(settings.getLibraryInfos(), new Function<LibraryInfo, Object>() {
      @Override
      public Object fun(LibraryInfo libraryInfo) {
        return new JCheckBox(libraryInfo.getPresentableName(), libraryInfo.isSelected());
      }
    })));
    myFilesToDownloadLabel.setLabelFor(myFilesList);

    myDirectoryField.addBrowseFolderListener(ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.title"),
                                             ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.description"), null,
                                             FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myCopyDownloadedFilesToLabel.setLabelFor(myDirectoryField);
    myDirectoryField.setText(settings.getDirectoryForDownloadedLibrariesPath());

    myDownloadSourcesCheckBox.setSelected(settings.isDownloadSources());
    myDownloadJavadocsCheckBox.setSelected(settings.isDownloadJavadocs());
    myLibraryNameField.setText(settings.getLibraryName());
    myLevelComboBox.setModel(new EnumComboBoxModel<LibrariesContainer.LibraryLevel>(LibrariesContainer.LibraryLevel.class));
    myLevelComboBox.setSelectedItem(settings.getLibraryLevel());
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFilesList;
  }

  @Override
  protected void doOKAction() {
    mySettings.setLibraryName(myLibraryNameField.getText());
    mySettings.setLibraryLevel((LibrariesContainer.LibraryLevel)myLevelComboBox.getSelectedItem());
    mySettings.setDirectoryForDownloadedLibrariesPath(myDirectoryField.getText());
    LibraryInfo[] libraryInfos = mySettings.getLibraryInfos();
    for (int i = 0, libraryInfosLength = libraryInfos.length; i < libraryInfosLength; i++) {
      LibraryInfo info = libraryInfos[i];
      info.setSelected(myFilesList.isItemSelected(i));
    }
    mySettings.setDownloadSources(myDownloadSourcesCheckBox.isSelected());
    mySettings.setDownloadJavadocs(myDownloadJavadocsCheckBox.isSelected());
    super.doOKAction();
  }
}
