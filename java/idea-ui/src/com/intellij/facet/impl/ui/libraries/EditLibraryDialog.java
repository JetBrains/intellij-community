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
  private JTextField myTextField1;
  private JComboBox myComboBox1;
  private JPanel myEditorPanel;
  private JPanel myPanel;

  protected EditLibraryDialog(Component parent) {
    super(parent, true);
    setTitle("Edit Library");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myLibrary = myLibraryTable.createLibrary("xxx");
      }
    });
    init();

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

    myEditorPanel.add(editor, BorderLayout.CENTER);
    return myPanel;
  }
}
