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
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibrariesAlphaComparator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureDialogCellAppearanceUtils;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.RadioButtonEnumModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class LibraryOptionsPanel {
  private JLabel myMessageLabel;
  private JPanel myPanel;
  private JButton myConfigureButton;
  private JComboBox myExistingLibraryComboBox;
  private JRadioButton myUseExistingLibraryRadioButton;
  private JRadioButton myDoNotCreateRadioButton;
  private JPanel myConfigurationPanel;
  private ButtonGroup myButtonGroup;

  private final LibraryCompositionSettings mySettings;
  private final LibrariesContainer myLibrariesContainer;
  private final List<Library> myLibraries;

  private enum Choice {
    USE_EXISTING,
    DOWNLOAD,
    PICK_FILES,
    DO_NOT_CREATE
  }

  private RadioButtonEnumModel<Choice> myButtonEnumModel;

  public LibraryOptionsPanel(@NotNull LibraryCompositionSettings settings,
                             @NotNull LibrariesContainer librariesContainer,
                             final boolean showDoNotCreateOption) {
    mySettings = settings;
    myLibrariesContainer = librariesContainer;
    myLibraries = calculateSuitableLibraries();

    myButtonEnumModel = RadioButtonEnumModel.bindEnum(Choice.class, myButtonGroup);
    myButtonEnumModel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateState();
      }
    });

    final boolean librariesFound = !myLibraries.isEmpty();
    myDoNotCreateRadioButton.setVisible(showDoNotCreateOption);
    myUseExistingLibraryRadioButton.setVisible(librariesFound);
    myExistingLibraryComboBox.setVisible(librariesFound);
    if (librariesFound) {
      final SortedComboBoxModel<Library> model = new SortedComboBoxModel<Library>(LibrariesAlphaComparator.INSTANCE);
      model.addAll(myLibraries);
      myExistingLibraryComboBox.setModel(model);
      myExistingLibraryComboBox.setSelectedIndex(0);
      myExistingLibraryComboBox.setRenderer(new ColoredListCellRenderer() {
        @Override
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof Library) {
            ProjectStructureDialogCellAppearanceUtils.forLibrary((Library)value, null).customize(this);
          }
        }
      });
    }
    myButtonEnumModel.setSelected(librariesFound ? Choice.USE_EXISTING : Choice.DOWNLOAD);

    myConfigureButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        switch (myButtonEnumModel.getSelected()) {
          case DOWNLOAD:
            new DownloadingOptionsDialog(myPanel, mySettings).show();
            break;
          case PICK_FILES:
            if (mySettings.getLibraryEditor() == null) {
              VirtualFile[] files = showFileChooser();
              mySettings.addFilesToLibrary(files, OrderRootType.CLASSES);
            }
            EditLibraryDialog dialog = new EditLibraryDialog(myPanel, mySettings);
            dialog.show();
            break;
          default:
            break;
        }
        updateState();
      }
    });

    updateState();
  }

  private List<Library> calculateSuitableLibraries() {
    LibraryInfo[] libraryInfos = mySettings.getLibraryInfos();
    RequiredLibrariesInfo requiredLibraries = new RequiredLibrariesInfo(libraryInfos);
    List<Library> suitableLibraries = new ArrayList<Library>();
    Library[] libraries = myLibrariesContainer.getAllLibraries();
    for (Library library : libraries) {
      RequiredLibrariesInfo.RequiredClassesNotFoundInfo info =
        requiredLibraries.checkLibraries(myLibrariesContainer.getLibraryFiles(library, OrderRootType.CLASSES), false);
      if (info == null) {
        suitableLibraries.add(library);
      }
    }
    return suitableLibraries;
  }

  private VirtualFile[] showFileChooser() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, false, false, true);
    descriptor.setTitle(IdeBundle.message("file.chooser.select.paths.title"));
    descriptor.setDescription(IdeBundle.message("file.chooser.multiselect.description"));
    return FileChooser.chooseFiles(myPanel, descriptor, getBaseDirectory());
  }

  @Nullable
  private VirtualFile getBaseDirectory() {
    String path = mySettings.getBaseDirectoryForDownloadedFiles();
    VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(path);
    if (dir == null) {
      path = path.substring(0, path.lastIndexOf('/'));
      dir = LocalFileSystem.getInstance().findFileByPath(path);
    }
    return dir;
  }

  private void updateState() {
    myMessageLabel.setForeground(Color.black);

    String message = "";
    boolean showConfigurePanel = true;
    switch (myButtonEnumModel.getSelected()) {
      case DOWNLOAD:
        message = getDownloadFilesMessage();
        break;
      case PICK_FILES:
        NewLibraryEditor libraryEditor = mySettings.getLibraryEditor();
        if (libraryEditor == null) {
          myMessageLabel.setForeground(Color.red);
          message = "Press 'Configure' button to add classes to the library";
        }
        else {
          message = MessageFormat.format("{0} level library <b>{1}</b>" +
                                         " with {2} file(s) will be created",
                                         mySettings.getLibraryLevel(),
                                         mySettings.getLibraryName(),
                                         libraryEditor.getFiles(OrderRootType.CLASSES).length);
        }
        break;
      default:
        //show the longest message on the hidden card to ensure that dialog won't jump if user selects another option
        message = getDownloadFilesMessage();
        showConfigurePanel = false;
    }

    ((CardLayout)myConfigurationPanel.getLayout()).show(myConfigurationPanel, showConfigurePanel ? "configure" : "empty");
    myMessageLabel.setText("<html>" + message + "</html>");
  }

  private String getDownloadFilesMessage() {
    final String downloadPath = mySettings.getDirectoryForDownloadedLibrariesPath();
    final String basePath = mySettings.getBaseDirectoryForDownloadedFiles();
    String path;
    if (FileUtil.startsWith(downloadPath, basePath)) {
      path = FileUtil.getRelativePath(basePath, downloadPath, File.separatorChar);
    }
    else {
      path = PathUtil.getFileName(downloadPath);
    }
    return MessageFormat.format("{0} jar(s) will be downloaded into <b>{1}</b> directory <br>" +
                                   "{2} library <b>{3}</b> will be created",
                                   mySettings.getLibraryInfos().length,
                                   path,
                                   mySettings.getLibraryLevel(),
                                   mySettings.getLibraryName());
  }

  public LibraryCompositionSettings getSettings() {
    return mySettings;
  }


  public void apply() {
    mySettings.setSelectedExistingLibrary((Library)myExistingLibraryComboBox.getSelectedItem());
    mySettings.setDownloadLibraries(myButtonEnumModel.getSelected() == Choice.DOWNLOAD);
  }

  public JComponent getMainPanel() {
    return myPanel;
  }
}
