// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class CreateNewLibraryDialog extends LibraryEditorDialogBase {
  private final StructureConfigurableContext myContext;
  private final NewLibraryEditor myLibraryEditor;
  private final ComboBox<LibraryTable> myLibraryLevelCombobox;

  public CreateNewLibraryDialog(@NotNull JComponent parent, @NotNull StructureConfigurableContext context, @NotNull NewLibraryEditor libraryEditor,
                                 @NotNull List<LibraryTable> libraryTables, int selectedTable) {
    super(parent, new LibraryRootsComponent(context.getProject(), libraryEditor));
    myContext = context;
    myLibraryEditor = libraryEditor;
    DefaultComboBoxModel<LibraryTable> model = new DefaultComboBoxModel<>();
    for (LibraryTable table : libraryTables) {
      model.addElement(table);
    }
    myLibraryLevelCombobox = new ComboBox<>(model);
    myLibraryLevelCombobox.setSelectedIndex(selectedTable);
    myLibraryLevelCombobox.setRenderer(SimpleListCellRenderer.create("", value -> value.getPresentation().getDisplayName(false)));
    init();
  }

  @Override
  protected @NotNull LibraryTable.ModifiableModel getTableModifiableModel() {
    final LibraryTable selectedTable = (LibraryTable)myLibraryLevelCombobox.getSelectedItem();
    return myContext.getModifiableLibraryTable(selectedTable);
  }

  public @NotNull Library createLibrary() {
    final LibraryTable.ModifiableModel modifiableModel = getTableModifiableModel();
    final LibraryType<?> type = myLibraryEditor.getType();
    final Library library = modifiableModel.createLibrary(myLibraryEditor.getName(), type != null ? type.getKind() : null);
    final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)library.getModifiableModel();
    myLibraryEditor.applyTo(model);
    WriteAction.run(() -> model.commit());
    return library;
  }

  @Override
  protected void addNorthComponents(FormBuilder formBuilder) {
    formBuilder.addLabeledComponent(JavaUiBundle.message("label.library.level"), myLibraryLevelCombobox);
  }

  @Override
  protected boolean shouldCheckName(String newName) {
    return true;
  }

  @Override
  protected @Nullable String getHelpId() {
    return "Create_Library_dialog";
  }
}
