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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;

/**
 * @author Dmitry Avdeev
 */
public class LibraryOptionsPanel {

  private JLabel myMessage;
  private JPanel myPanel;
  private JRadioButton myDownloadFromMavenButton;
  private JRadioButton myDoNotCreateButton;
  private JButton myConfigureButton;
  private JRadioButton myLocateOnDiskButton;

  private LibraryCompositionSettings myLibraryCompositionSettings;

  public LibraryOptionsPanel(LibraryCompositionSettings libraryCompositionSettings) {
    myLibraryCompositionSettings = libraryCompositionSettings;
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateState();
      }
    };
    myDownloadFromMavenButton.addActionListener(listener);
    myLocateOnDiskButton.addActionListener(listener);
    myDoNotCreateButton.addActionListener(listener);

    myConfigureButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final Point point = myConfigureButton.getLocationOnScreen();
        if (myLocateOnDiskButton.isSelected()) {
          EditLibraryDialog dialog = new EditLibraryDialog(myConfigureButton) {
            @Override
            public Point getInitialLocation() {
              point.translate(- 50, - getSize().height);
              return point;
            }
          };
          dialog.show();
        }
        else {
          new DownloadingOptionsDialog(myConfigureButton, myLibraryCompositionSettings) {
            @Override
            public Point getInitialLocation() {
              point.translate(- 50, - getSize().height);
              return point;
            }
          }.show();
        }
      }
    });

    updateState();
  }


  private VirtualFile[] showFileChooser() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, false, false, true);
    descriptor.setTitle(IdeBundle.message("file.chooser.select.paths.title"));
    descriptor.setDescription(IdeBundle.message("file.chooser.multiselect.description"));
    return FileChooser.chooseFiles(myConfigureButton, descriptor, getBaseDirectory());
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
  
  private void updateState() {
    if (myMessage.isEnabled()) {
      myMessage.setForeground(Color.black);
    }
    myConfigureButton.setEnabled(true);
    if (myDownloadFromMavenButton.isSelected()) {
      String path = myLibraryCompositionSettings.getDirectoryForDownloadedLibrariesPath().substring(myLibraryCompositionSettings.getBaseDirectoryForDownloadedFiles().length());
      String message = MessageFormat.format("<html>{0} jar(s) will be downloaded into <b>{1}</b> directory<br> " +
                                            "{2} level library <b>{3}</b> will be created</html>",
                                            myLibraryCompositionSettings.getLibraryInfos().length,
                                            path,
                                            myLibraryCompositionSettings.getLibraryLevel(),
                                            myLibraryCompositionSettings.getLibraryName());
      myMessage.setText(message);
    }
    else if (myLocateOnDiskButton.isSelected()) {
      if (myLibraryCompositionSettings.getAddedJars().isEmpty()) {
        myMessage.setForeground(Color.red);
        myMessage.setText("Press Configure button to add classes to the library");
      }
      else {
        String message = MessageFormat.format("<html>{0} level library <b>{1}</b><br>" +
                                              "with {2} file(s) will be created</html>",
                                              myLibraryCompositionSettings.getLibraryLevel(),
                                              myLibraryCompositionSettings.getLibraryName(),
                                              myLibraryCompositionSettings.getAddedJars().size());
        myMessage.setText(message);
      }
    }
    else {
      myMessage.setText("<html>No library will be created<br>" +
                        "You can add it later manually</html>");
      myConfigureButton.setEnabled(false);
    }
  }

  public LibraryCompositionSettings getLibraryCompositionSettings() {
    return myLibraryCompositionSettings;
  }


  public void apply() {

  }

  public JComponent getMainPanel() {
    return myPanel;
  }
}
