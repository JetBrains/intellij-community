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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.impl.ModuleLibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
* @author nik
*/
public abstract class LibraryEditorDialogBase extends DialogWrapper {
  private JTextField myNameField;
  private LibraryRootsComponent myLibraryRootsComponent;

  public LibraryEditorDialogBase(final Component parent, final LibraryRootsComponent libraryRootsComponent) {
    super(parent, true);
    myLibraryRootsComponent = libraryRootsComponent;
    setTitle(ProjectBundle.message("library.configure.title"));
    Disposer.register(getDisposable(), myLibraryRootsComponent);
  }

  public <T> void addFileChooserContext(DataKey<T> key, T value) {
    myLibraryRootsComponent.addFileChooserContext(key, value);
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected final void doOKAction() {
    if (!validateAndApply()) {
      return;
    }
    super.doOKAction();
  }

  protected boolean validateAndApply() {
    final String currentName = myLibraryRootsComponent.getLibraryEditor().getName();
    String newName = myNameField.getText().trim();
    if (newName.length() == 0) {
      newName = null;
    }
    if (!Comparing.equal(newName, currentName)) {
      final LibraryTable.ModifiableModel tableModifiableModel = getTableModifiableModel();
      if (tableModifiableModel != null && !(tableModifiableModel instanceof ModuleLibraryTable)) {
        if (newName == null) {
          Messages.showErrorDialog(ProjectBundle.message("library.name.not.specified.error", newName), ProjectBundle.message("library.name.not.specified.title"));
          return false;
        }
        if (LibraryRootsComponent.libraryAlreadyExists(tableModifiableModel, newName)) {
          Messages.showErrorDialog(ProjectBundle.message("library.name.already.exists.error", newName), ProjectBundle.message("library.name.already.exists.title"));
          return false;
        }
      }
      myLibraryRootsComponent.renameLibrary(newName);
    }
    return true;
  }

  @Nullable
  protected LibraryTable.ModifiableModel getTableModifiableModel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    FormBuilder formBuilder = new FormBuilder();
    String currentName = myLibraryRootsComponent.getLibraryEditor().getName();
    myNameField = new JTextField(currentName);
    formBuilder.addLabeledComponent("&Name:", myNameField);
    addNorthComponents(formBuilder);
    myNameField.selectAll();

    final JPanel panel = formBuilder.getPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
    return panel;
  }

  protected void addNorthComponents(FormBuilder formBuilder) {
  }

  protected JComponent createCenterPanel() {
    return myLibraryRootsComponent.getComponent();
  }
}
