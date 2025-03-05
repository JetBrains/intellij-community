// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.impl.ModuleLibraryTableBase;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class LibraryEditorDialogBase extends DialogWrapper {
  protected JTextField myNameField;
  private final LibraryRootsComponent myLibraryRootsComponent;

  public LibraryEditorDialogBase(final Component parent, final LibraryRootsComponent libraryRootsComponent) {
    super(parent, true);
    myLibraryRootsComponent = libraryRootsComponent;
    libraryRootsComponent.resetProperties();
    setTitle(JavaUiBundle.message("library.configure.title"));
    Disposer.register(getDisposable(), myLibraryRootsComponent);
  }

  public void setContextModule(Module module) {
    myLibraryRootsComponent.setContextModule(module);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected final void doOKAction() {
    if (!validateAndApply()) {
      return;
    }
    super.doOKAction();
  }

  protected boolean validateAndApply() {
    String newName = myNameField.getText().trim();
    if (newName.isEmpty()) {
      newName = null;
    }
    if (shouldCheckName(newName)) {
      final LibraryTable.ModifiableModel tableModifiableModel = getTableModifiableModel();
      if (tableModifiableModel != null && !(tableModifiableModel instanceof ModuleLibraryTableBase)) {
        if (newName == null) {
          Messages.showErrorDialog(JavaUiBundle.message("library.name.not.specified.error"), JavaUiBundle.message("library.name.not.specified.title"));
          return false;
        }
        if (LibraryEditingUtil.libraryAlreadyExists(tableModifiableModel, newName)) {
          Messages.showErrorDialog(JavaUiBundle.message("library.name.already.exists.error", newName), JavaUiBundle.message("library.name.already.exists.title"));
          return false;
        }
      }
      myLibraryRootsComponent.renameLibrary(newName);
    }
    myLibraryRootsComponent.applyProperties();
    return true;
  }

  protected abstract boolean shouldCheckName(String newName);

  protected @Nullable LibraryTable.ModifiableModel getTableModifiableModel() {
    return null;
  }

  protected LibraryRootsComponent getLibraryRootsComponent() {
    return myLibraryRootsComponent;
  }

  @Override
  protected JComponent createNorthPanel() {
    String currentName = myLibraryRootsComponent.getLibraryEditor().getName();
    myNameField = new JTextField(currentName);
    myNameField.selectAll();

    FormBuilder formBuilder = FormBuilder.createFormBuilder().addLabeledComponent(JavaUiBundle.message("label.library.name"), myNameField);
    addNorthComponents(formBuilder);
    return formBuilder.addVerticalGap(10).getPanel();
  }

  protected void addNorthComponents(FormBuilder formBuilder) {
  }

  @Override
  protected JComponent createCenterPanel() {
    return myLibraryRootsComponent.getComponent();
  }
}
