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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author nik
 */
public class EditExistingLibraryDialog extends LibraryEditorDialogBase {
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
    setTitle("Configure " + presentation.getDisplayName(false));
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
    return !Comparing.equal(newName, getLibraryRootsComponent().getLibraryEditor().getName());
  }
}
