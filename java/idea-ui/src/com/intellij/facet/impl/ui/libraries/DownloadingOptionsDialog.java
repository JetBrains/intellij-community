/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.framework.library.DownloadableLibraryFileDescription;
import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryNameAndLevelPanel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CheckBoxListListener;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class DownloadingOptionsDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ui.libraries.DownloadingOptionsDialog");

  private enum AdditionalDownloadType {SOURCES, DOCUMENTATION}

  private JPanel myPanel;
  private CheckBoxList myFilesList;
  private TextFieldWithBrowseButton myDirectoryField;
  private JCheckBox myDownloadSourcesCheckBox;
  private JCheckBox myDownloadJavadocsCheckBox;
  private JLabel myFilesToDownloadLabel;
  private JLabel myCopyDownloadedFilesToLabel;
  private JPanel myNameWrappingPanel;
  private JComboBox myVersionComboBox;
  private final LibraryNameAndLevelPanel myNameAndLevelPanel;
  private DownloadableLibraryType myLibraryType;
  private FrameworkLibraryVersion myLastSelectedVersion;

  public DownloadingOptionsDialog(@NotNull Component parent, @NotNull final LibraryDownloadSettings settings, @NotNull List<? extends FrameworkLibraryVersion> versions,
                                  final boolean showNameAndLevel) {
    super(parent, true);
    setTitle("Downloading Options");
    myLibraryType = settings.getLibraryType();
    LOG.assertTrue(!versions.isEmpty());

    final FormBuilder builder = LibraryNameAndLevelPanel.createFormBuilder();

    myVersionComboBox = new ComboBox();
    for (FrameworkLibraryVersion version : versions) {
      myVersionComboBox.addItem(version);
    }
    myVersionComboBox.setRenderer(new ListCellRendererWrapper<FrameworkLibraryVersion>() {
      @Override
      public void customize(JList list, FrameworkLibraryVersion value, int index, boolean selected, boolean hasFocus) {
        setText(value.getDefaultLibraryName());
      }
    });
    myVersionComboBox.setSelectedItem(settings.getVersion());
    if (versions.size() > 1) {
      builder.addLabeledComponent("&Version:", myVersionComboBox);
    }

    if (showNameAndLevel) {
      myNameAndLevelPanel = new LibraryNameAndLevelPanel(builder, settings.getLibraryName(), settings.getLibraryLevel());
    }
    else {
      myNameAndLevelPanel = null;
    }
    myNameWrappingPanel.add(builder.getPanel());

    onVersionChanged(settings.getSelectedDownloads());
    myVersionComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onVersionChanged(null);
      }
    });

    myFilesList.setBorder(null);
    myFilesToDownloadLabel.setLabelFor(myFilesList);
    myDirectoryField.addBrowseFolderListener(ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.title"),
                                             ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.description"), null,
                                             FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myCopyDownloadedFilesToLabel.setLabelFor(myDirectoryField);
    myDirectoryField.setText(FileUtil.toSystemDependentName(settings.getDirectoryForDownloadedLibrariesPath()));

    boolean sourcesCheckboxVisible = false;
    boolean javadocCheckboxVisible = false;
    for (FrameworkLibraryVersion version : versions) {
      sourcesCheckboxVisible |= haveAdditionalDownloads(version.getFiles(), AdditionalDownloadType.SOURCES);
      javadocCheckboxVisible |= haveAdditionalDownloads(version.getFiles(), AdditionalDownloadType.DOCUMENTATION);
    }
    myDownloadSourcesCheckBox.setVisible(sourcesCheckboxVisible);
    myDownloadJavadocsCheckBox.setVisible(javadocCheckboxVisible);
    myFilesList.setCheckBoxListListener(new CheckBoxListListener() {
      @Override
      public void checkBoxSelectionChanged(int index, boolean value) {
        updateSourcesAndJavadocCheckboxes();
      }
    });

    updateSourcesAndJavadocCheckboxes();
    myDownloadSourcesCheckBox.setSelected(settings.isDownloadSources());
    myDownloadJavadocsCheckBox.setSelected(settings.isDownloadJavaDocs());
    init();
  }

  private void updateSourcesAndJavadocCheckboxes() {
    final FrameworkLibraryVersion version = getSelectedVersion();
    boolean sourcesCheckboxEnabled;
    boolean javadocCheckboxEnabled;
    if (version == null) {
      sourcesCheckboxEnabled = javadocCheckboxEnabled = false;
    }
    else {
      final List<DownloadableLibraryFileDescription> descriptions = getSelectedDownloads(version);
      sourcesCheckboxEnabled = haveAdditionalDownloads(descriptions, AdditionalDownloadType.SOURCES);
      javadocCheckboxEnabled = haveAdditionalDownloads(descriptions, AdditionalDownloadType.DOCUMENTATION);
    }
    setEnabled(myDownloadSourcesCheckBox, sourcesCheckboxEnabled);
    setEnabled(myDownloadJavadocsCheckBox, javadocCheckboxEnabled);
  }

  private static void setEnabled(final JCheckBox checkBox, final boolean enabled) {
    if (!enabled) {
      checkBox.setSelected(false);
    }
    checkBox.setEnabled(enabled);
  }

  private static boolean haveAdditionalDownloads(final List<? extends DownloadableLibraryFileDescription> descriptions, AdditionalDownloadType type) {
    for (DownloadableLibraryFileDescription description : descriptions) {
      if (type == AdditionalDownloadType.SOURCES && description.getSourcesDescription() != null
        || type == AdditionalDownloadType.DOCUMENTATION && description.getDocumentationDescription() != null) {
        return true;
      }
    }
    return false;
  }

  private void onVersionChanged(final @Nullable List<? extends DownloadableLibraryFileDescription> selectedFiles) {
    final FrameworkLibraryVersion version = getSelectedVersion();
    if (Comparing.equal(myLastSelectedVersion, version)) return;

    if (version != null) {
      final List<? extends DownloadableLibraryFileDescription> downloads = version.getFiles();
      myFilesList.setModel(new CollectionListModel<>(
        ContainerUtil.map2Array(downloads, JCheckBox.class, new Function<DownloadableLibraryFileDescription, JCheckBox>() {
          @Override
          public JCheckBox fun(DownloadableLibraryFileDescription description) {
            final boolean selected = selectedFiles != null ? selectedFiles.contains(description) : !description.isOptional();
            return new JCheckBox(description.getPresentableFileName(), selected);
          }
        })));
      if (myNameAndLevelPanel != null) {
        myNameAndLevelPanel.setDefaultName(version.getDefaultLibraryName());
      }
    }
    updateSourcesAndJavadocCheckboxes();
    myLastSelectedVersion = version;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFilesList;
  }

  @Nullable
  public FrameworkLibraryVersion getSelectedVersion() {
    return (FrameworkLibraryVersion)myVersionComboBox.getSelectedItem();
  }

  @Nullable
  public static LibraryDownloadSettings showDialog(@NotNull JComponent parent,
                                                   @NotNull LibraryDownloadSettings settings,
                                                   List<? extends FrameworkLibraryVersion> versions,
                                                   boolean showNameAndLevel) {
    final DownloadingOptionsDialog dialog = new DownloadingOptionsDialog(parent, settings, versions, showNameAndLevel);
    if (!dialog.showAndGet()) {
      return null;
    }

    return dialog.createSettings();
  }

  private List<DownloadableLibraryFileDescription> getSelectedDownloads(FrameworkLibraryVersion version) {
    List<DownloadableLibraryFileDescription> selected = new ArrayList<>();
    List<? extends DownloadableLibraryFileDescription> downloads = version.getFiles();
    for (int i = 0; i < downloads.size(); i++) {
      if (myFilesList.isItemSelected(i)) {
        selected.add(downloads.get(i));
      }
    }
    return selected;
  }

  private LibraryDownloadSettings createSettings() {
    final FrameworkLibraryVersion version = getSelectedVersion();
    LOG.assertTrue(version != null);
    String libraryName;
    LibrariesContainer.LibraryLevel libraryLevel;
    if (myNameAndLevelPanel != null) {
      libraryName = myNameAndLevelPanel.getLibraryName();
      libraryLevel = myNameAndLevelPanel.getLibraryLevel();
    }
    else {
      libraryName = version.getDefaultLibraryName();
      libraryLevel = LibrariesContainer.LibraryLevel.PROJECT;
    }

    final String path = FileUtil.toSystemIndependentName(myDirectoryField.getText());
    List<DownloadableLibraryFileDescription> selected = getSelectedDownloads(version);

    return new LibraryDownloadSettings(version, myLibraryType, path, libraryName, libraryLevel, selected,
                                       myDownloadSourcesCheckBox.isSelected(), myDownloadJavadocsCheckBox.isSelected());
  }
}
