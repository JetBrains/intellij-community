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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public class EditLibraryDialog extends DialogWrapper {

  private ApplicationLibraryTable myLibraryTable = new ApplicationLibraryTable();
  private Library myLibrary;
  private JPanel myEditorPanel;
  private JPanel myPanel;
  private JPanel myNameAndLevelPanelWrapper;
  private final LibraryNameAndLevelPanel myNameAndLevelPanel;
  private LibraryCompositionSettings mySettings;

  public EditLibraryDialog(Component parent, LibraryCompositionSettings settings) {
    super(parent, true);
    mySettings = settings;
    setTitle("Edit Library");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myLibrary = myLibraryTable.createLibrary("xxx");
      }
    });

    myNameAndLevelPanel = new LibraryNameAndLevelPanel();
    myNameAndLevelPanel.reset(mySettings);
    init();

  }

  public Library getLibrary() {
    return myLibrary;
  }

  @Override
  protected JComponent createCenterPanel() {

    JComponent editor = LibraryTableEditor.editLibrary(new LibraryTableModifiableModelProvider() {

      @Override
      public LibraryTable.ModifiableModel getModifiableModel() {
        return myLibraryTable.getModifiableModel();
      }

      @Override
      public String getTableLevel() {
        return myLibraryTable.getTableLevel();
      }

      @Override
      public LibraryTablePresentation getLibraryTablePresentation() {
        return myLibraryTable.getPresentation();
      }

      @Override
      public boolean isLibraryTableEditable() {
        return false;
      }
    }, myLibrary).getComponent();

    myEditorPanel.add(editor);
    myNameAndLevelPanelWrapper.add(myNameAndLevelPanel.getPanel());
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    myNameAndLevelPanel.apply(mySettings);
    super.doOKAction();
  }
}
