/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public class LocateLibraryDialog extends DialogWrapper {
  private JPanel contentPane;
  private JTextPane myDescription;
  private JLabel myFileLabel;
  private TextFieldWithBrowseButton myLibraryFile;
  private JRadioButton myAddThisFileRadioButton;
  private JRadioButton myCopyToRadioButton;
  private TextFieldWithBrowseButton myCopyToDir;

  private final Project myProject;
  private String myResultingLibraryPath;

  public String getResultingLibraryPath() {
    return myResultingLibraryPath;
  }

  public LocateLibraryDialog(Module module, String libraryPath, @NonNls final String libraryName, final String libraryDescription ) {
    super (module.getProject(), true);
    setTitle ( QuickFixBundle.message("add.library.title.dialog"));

    myProject = module.getProject();

    // copied from Messages.MessageDialog
    JLabel label = new JLabel();
    myDescription.setFont(label.getFont());
    myDescription.setBackground(UIUtil.getOptionPaneBackground());
    myDescription.setForeground(label.getForeground());
    myDescription.setText(libraryDescription);
    // end of copy
    
    myFileLabel.setLabelFor(myLibraryFile.getTextField());

    myLibraryFile.setText(new File ( libraryPath, libraryName).getPath() );
    myLibraryFile.addBrowseFolderListener(QuickFixBundle.message("add.library.title.locate.library"),
                                          QuickFixBundle.message("add.library.description.locate.library"), myProject,
                                          new FileChooserDescriptor(false,false,true,false,false,false));

    myCopyToDir.setText(new File (module.getModuleFilePath()).getParent());
    myCopyToDir.addBrowseFolderListener(QuickFixBundle.message("add.library.title.choose.folder"),
                                        QuickFixBundle.message("add.library.description.choose.folder"), myProject,
                                        FileChooserDescriptorFactory.createSingleFolderDescriptor());

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        updateButtons();
      }
    };

    myAddThisFileRadioButton.addActionListener(listener);
    myCopyToRadioButton.addActionListener(listener);

    myAddThisFileRadioButton.setSelected(true);

    myCopyToDir.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateButtons();
      }
    });

    updateButtons();

    init ();
  }

  private void updateButtons() {
    final boolean copyEnabled = myCopyToRadioButton.isSelected();
    myCopyToDir.setEnabled(copyEnabled);
    if ( copyEnabled ) {
      myCopyToDir.getTextField().requestFocusInWindow();
    }
    setOKActionEnabled(! copyEnabled || !myCopyToDir.getText().isEmpty());
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "#org.jetbrains.codeInsight.daemon.impl.quickfix.LocateLibraryDialog";
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  protected void doOKAction() {
    if (getOKAction().isEnabled()) {
      myResultingLibraryPath = getResultingPath();
      if ( myResultingLibraryPath != null ) {
        close(OK_EXIT_CODE);
      }
    }
  }

  @Nullable
  private String getResultingPath() {
    final File srcFile = new File(myLibraryFile.getText());
    if (!srcFile.exists()) {
      Messages.showErrorDialog(myProject, QuickFixBundle.message("add.library.error.not.found", srcFile.getPath()),
                               QuickFixBundle.message("add.library.title.error"));
      return null;
    }

    if (!myCopyToRadioButton.isSelected()) {
      return srcFile.getPath();
    }

    final String dstDir = myCopyToDir.getText();
    if (dstDir.isEmpty()) {
      return null;
    }
    
    File dstFile = new File(dstDir, srcFile.getName());
    try {
      FileUtil.copy(srcFile, dstFile);
      return dstFile.getPath();
    }
    catch (IOException e) {
      Messages.showErrorDialog(myProject,
                               QuickFixBundle.message("add.library.error.cannot.copy", srcFile.getPath(), dstFile.getPath(), e.getMessage()),
                               QuickFixBundle.message("add.library.title.error"));
      return null;
    }
  }
}
