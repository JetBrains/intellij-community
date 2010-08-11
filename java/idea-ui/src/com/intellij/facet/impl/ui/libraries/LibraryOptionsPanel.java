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
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.RadioButtonEnumModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class LibraryOptionsPanel {

  private JLabel myMessage;
  private JPanel myPanel;
  private JButton myConfigureButton;
  private JPanel myExistingLibrariesPanel;
  private JLabel myExistingLibrariesLabel;
  private ButtonGroup myButtonGroup;
  private ElementsChooser<Library> myLibrariesChooser;

  private LibraryCompositionSettings mySettings;
  private LibrariesContainer myLibrariesContainer;


  private enum Choice {
    DOWNLOAD,
    PICK_FILES,
    DO_NOT_CREATE
  }

  private RadioButtonEnumModel<Choice> myButtonEnumModel;

  public LibraryOptionsPanel(LibraryCompositionSettings settings, LibrariesContainer librariesContainer) {

    mySettings = settings;
    myLibrariesContainer = librariesContainer;

    List<Library> suitableLibraries = calculateSuitableLibraries();
    if (!suitableLibraries.isEmpty()) {
      mySettings.setUsedLibraries(suitableLibraries);
    }

    myLibrariesChooser = new ChooseLibrariesDialog.LibraryElementChooser(suitableLibraries);
    myLibrariesChooser.getComponents()[0].setPreferredSize(new Dimension(10, 10)); // this makes scrollbars to work
    myExistingLibrariesPanel.add(myLibrariesChooser);
    myExistingLibrariesLabel.setLabelFor(myLibrariesChooser.getComponent());

    myButtonEnumModel = RadioButtonEnumModel.bindEnum(Choice.class, myButtonGroup);
    myButtonEnumModel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateState();
      }
    });

    myConfigureButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        switch (myButtonEnumModel.getSelected()) {
          case DOWNLOAD:
            showDialog(new DownloadingOptionsDialog(myConfigureButton, mySettings));
            break;
          case PICK_FILES:
            if (mySettings.getLibrary() == null) {
              VirtualFile[] files = showFileChooser();
              final Library.ModifiableModel modifiableModel = mySettings.getOrCreateLibrary().getModifiableModel();
              for (VirtualFile file : files) {
                modifiableModel.addRoot(file, OrderRootType.CLASSES);
              }
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  modifiableModel.commit();
                }
              });
            }
            EditLibraryDialog dialog = new EditLibraryDialog(myConfigureButton, mySettings);
            showDialog(dialog);
            break;
          case DO_NOT_CREATE:
            break;
        }
        updateState();
      }
    });

    updateState();
  }

  private void showDialog(final DialogWrapper dialog) {
    dialog.setInitialLocationCallback(new Computable<Point>() {
      @Override
      public Point compute() {
        Point point = myConfigureButton.getLocationOnScreen();
        point.translate(- 50, - dialog.getSize().height - 20);
        return point;
      }
    });
    dialog.show();
  }

  private List<Library> calculateSuitableLibraries() {
    LibraryInfo[] libraryInfos = mySettings.getLibraryInfos();
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

  private VirtualFile[] showFileChooser() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, false, false, true);
    descriptor.setTitle(IdeBundle.message("file.chooser.select.paths.title"));
    descriptor.setDescription(IdeBundle.message("file.chooser.multiselect.description"));
    return FileChooser.chooseFiles(myConfigureButton, descriptor, getBaseDirectory());
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
    if (myMessage.isEnabled()) {
      myMessage.setForeground(Color.black);
    }
    myConfigureButton.setEnabled(true);

    String message = "";

    switch (myButtonEnumModel.getSelected()) {
      case DOWNLOAD:
        String path = mySettings.getDirectoryForDownloadedLibrariesPath()
          .substring(mySettings.getBaseDirectoryForDownloadedFiles().length());
        message = MessageFormat.format("{0} jar(s) will be downloaded into <b>{1}</b> directory <br>" +
                                       "{2} library <b>{3}</b> will be created",
                                       mySettings.getLibraryInfos().length,
                                       path,
                                       mySettings.getLibraryLevel(),
                                       mySettings.getLibraryName());
        break;
      case PICK_FILES:
        Library library = mySettings.getLibrary();
        if (library == null) {
          myMessage.setForeground(Color.red);
          message = "Press Configure button to add classes to the library";
        }
        else {
          message = MessageFormat.format("{0} level library <b>{1}</b>" +
                                         " with {2} file(s) will be created",
                                         mySettings.getLibraryLevel(),
                                         mySettings.getLibraryName(),
                                         library.getFiles(OrderRootType.CLASSES).length);
        }
        break;
      case DO_NOT_CREATE:
        message = "No new library will be created";
        myConfigureButton.setEnabled(false);
        break;
    }

    myMessage.setText("<html>" + message + "</html>");
  }

  public LibraryCompositionSettings getSettings() {
    return mySettings;
  }


  public void apply() {
    mySettings.setUsedLibraries(myLibrariesChooser.getMarkedElements());
    mySettings.setDownloadLibraries(myButtonEnumModel.getSelected() == Choice.DOWNLOAD);
  }

  public JComponent getMainPanel() {
    return myPanel;
  }
}
