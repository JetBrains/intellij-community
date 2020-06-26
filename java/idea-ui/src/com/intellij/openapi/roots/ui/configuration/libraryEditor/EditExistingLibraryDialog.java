// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Objects;

public final class EditExistingLibraryDialog extends LibraryEditorDialogBase {
  private final ExistingLibraryEditor myLibraryEditor;
  private final boolean myCommitChanges;
  private final LibraryTable.ModifiableModel myTableModifiableModel;

  public static EditExistingLibraryDialog createDialog(Component parent,
                                                       LibraryTableModifiableModelProvider modelProvider,
                                                       Library library,
                                                       @Nullable Project project,
                                                       LibraryTablePresentation presentation,
                                                       StructureConfigurableContext context) {
    LibraryTable.ModifiableModel modifiableModel = modelProvider.getModifiableModel();
    boolean commitChanges = false;
    ExistingLibraryEditor libraryEditor;
    if (modifiableModel instanceof LibrariesModifiableModel) {
      libraryEditor = ((LibrariesModifiableModel)modifiableModel).getLibraryEditor(library);
    }
    else {
      libraryEditor = new ExistingLibraryEditor(library, context);
      commitChanges = true;
    }
    return new EditExistingLibraryDialog(parent, modifiableModel, project, libraryEditor, commitChanges, presentation, context);
  }

  private EditExistingLibraryDialog(Component parent,
                                    LibraryTable.ModifiableModel tableModifiableModel,
                                    @Nullable Project project,
                                    ExistingLibraryEditor libraryEditor,
                                    boolean commitChanges,
                                    LibraryTablePresentation presentation, StructureConfigurableContext context) {
    super(parent, new LibraryRootsComponent(project, libraryEditor));
    setTitle(JavaUiBundle.message("dialog.title.configure.library.0", presentation.getDisplayName(false)));
    myTableModifiableModel = tableModifiableModel;
    myLibraryEditor = libraryEditor;
    myCommitChanges = commitChanges;
    if (commitChanges) {
      Disposer.register(getDisposable(), libraryEditor);
    }
    context.addLibraryEditorListener(new LibraryEditorListener() {
      @Override
      public void libraryRenamed(@NotNull Library library, String oldName, String newName) {
        if (library.equals(myLibraryEditor.getLibrary())) {
          myNameField.setText(newName);
        }
      }
    }, getDisposable());
    init();
  }

  @Override
  protected boolean validateAndApply() {
    if (!super.validateAndApply()) {
      return false;
    }

    if (myCommitChanges) {
      myLibraryEditor.commit();
    }
    return true;
  }

  @Override
  protected LibraryTable.ModifiableModel getTableModifiableModel() {
    return myTableModifiableModel;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "Configure_Library_Dialog";
  }

  @Override
  protected boolean shouldCheckName(String newName) {
    return !Objects.equals(newName, getLibraryRootsComponent().getLibraryEditor().getName());
  }
}
