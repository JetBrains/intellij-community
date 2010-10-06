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

import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryDownloadDescription;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CollectionListModel;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class DownloadingOptionsDialog extends DialogWrapper {
  private JPanel myPanel;
  private CheckBoxList myFilesList;
  private TextFieldWithBrowseButton myDirectoryField;
  private JCheckBox myDownloadSourcesCheckBox;
  private JLabel myFilesToDownloadLabel;
  private JLabel myCopyDownloadedFilesToLabel;
  private JCheckBox myDownloadJavadocsCheckBox;
  private JPanel myNameWrappingPanel;
  private final LibraryDownloadSettings mySettings;
  private final LibraryNameAndLevelPanel myNameAndLevelPanel;
  private LibraryDownloadDescription myDownloadDescription;

  protected DownloadingOptionsDialog(@NotNull Component parent, @NotNull LibraryDownloadSettings settings) {
    super(parent, true);
    setTitle("Downloading Options");
    mySettings = settings;

    myDownloadDescription = settings.getDescription();
    final List<LibraryDownloadInfo> downloads = myDownloadDescription.getDownloads();
    myFilesList.setModel(new CollectionListModel(ContainerUtil.map2Array(downloads, JCheckBox.class, new Function<LibraryDownloadInfo, JCheckBox>() {
      @Override
      public JCheckBox fun(LibraryDownloadInfo libraryInfo) {
        return new JCheckBox(libraryInfo.getFileNamePrefix(), mySettings.getSelectedDownloads().contains(libraryInfo));
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

    myNameAndLevelPanel = new LibraryNameAndLevelPanel(settings.getLibraryName(), settings.getLibraryLevel());
    myNameWrappingPanel.add(myNameAndLevelPanel.getPanel());
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
    mySettings.setLibraryName(myNameAndLevelPanel.getLibraryName());
    mySettings.setLibraryLevel(myNameAndLevelPanel.getLibraryLevel());
    mySettings.setDirectoryForDownloadedLibrariesPath(myDirectoryField.getText());

    List<LibraryDownloadInfo> selected = new ArrayList<LibraryDownloadInfo>();
    List<LibraryDownloadInfo> downloads = myDownloadDescription.getDownloads();
    for (int i = 0; i < downloads.size(); i++) {
      if (myFilesList.isItemSelected(i)) {
        selected.add(downloads.get(i));
      }
    }
    mySettings.setSelectedDownloads(selected);
    mySettings.setDownloadSources(myDownloadSourcesCheckBox.isSelected());
    mySettings.setDownloadJavadocs(myDownloadJavadocsCheckBox.isSelected());
    super.doOKAction();
  }
}
