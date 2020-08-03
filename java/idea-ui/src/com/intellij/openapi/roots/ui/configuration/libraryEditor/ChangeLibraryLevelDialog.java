// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ChangeLibraryLevelDialog extends DialogWrapper {
  private JTextField myNameField;
  private JCheckBox myCopyFilesCheckBox;
  private TextFieldWithBrowseButton myDirectoryForFilesField;
  private JPanel myMainPanel;
  private final boolean myAllowEmptyName;
  private final LibraryTable.ModifiableModel myModifiableModel;

  public ChangeLibraryLevelDialog(JComponent parent,
                                  Project project,
                                  boolean copy,
                                  String libraryName,
                                  String path,
                                  boolean allowEmptyName,
                                  LibraryTableModifiableModelProvider provider) {
    super(parent, true);
    myAllowEmptyName = allowEmptyName;
    setTitle(JavaUiBundle.message(copy ? "dialog.title.0.library.copy" : "dialog.title.0.library.move"));
    myCopyFilesCheckBox.setText(JavaUiBundle.message(copy? "checkbox.0.library.files.to.copy" : "checkbox.0.library.files.to.move"));
    myCopyFilesCheckBox.setMnemonic(copy ? 'C' : 'M');
    myCopyFilesCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDirectoriesForFilesField();
      }
    });
    myModifiableModel = provider.getModifiableModel();
    myNameField.setText(libraryName);
    myDirectoryForFilesField.addBrowseFolderListener(JavaUiBundle.message("chooser.title.directory.for.library.files"), null, project,
                                                     FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myDirectoryForFilesField.setText(FileUtil.toSystemDependentName(path));
    myNameField.selectAll();
    init();
    checkName();
    updateDirectoriesForFilesField();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        checkName();
      }
    });
  }

  private void checkName() {
    final String name = getLibraryName();
    if (name.isEmpty()) {
      if (!myAllowEmptyName) {
        setErrorText(JavaUiBundle.message("error.message.library.name.is.not.specified"), myNameField);
      }
      return;
    }
    if (LibraryEditingUtil.libraryAlreadyExists(myModifiableModel, name)) {
      setErrorText(JavaUiBundle.message("error.message.library.0.already.exists", name), myNameField);
      return;
    }
    setErrorText(null);
  }

  private void updateDirectoriesForFilesField() {
    myDirectoryForFilesField.setEnabled(myCopyFilesCheckBox.isSelected());
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public String getLibraryName() {
    return myNameField.getText().trim();
  }

  @Nullable
  public String getDirectoryForFilesPath() {
    if (!myCopyFilesCheckBox.isSelected()) {
      return null;
    }
    return FileUtil.toSystemIndependentName(myDirectoryForFilesField.getText());
  }

}
