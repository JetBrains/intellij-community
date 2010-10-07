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
package com.intellij.ide.scriptingContext.ui;

import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibrariesPanel {
  private JPanel myTopPanel;
  private JButton myAddLibraryButton;
  private JButton myRemoveLibraryButton;
  private JButton myEditLibraryButton;
  private JPanel myScriptingLibrariesPanel;
  private JBTable myLibraryTable;
  private ScriptingLibraryTableModel myLibTableModel;
  private boolean myModified;

  public ScriptingLibrariesPanel(LibraryTable libTable) {
    myLibTableModel = new ScriptingLibraryTableModel(libTable);
    myLibraryTable.setModel(myLibTableModel);
    myAddLibraryButton.addActionListener(new ActionListener(){
      @Override
      public void actionPerformed(ActionEvent e) {
        addLibrary();
      }
    });
    if (libTable == null) {
      myAddLibraryButton.setEnabled(false);
    }
    myRemoveLibraryButton.setEnabled(false);
    myEditLibraryButton.setEnabled(false);
    myModified = false;
  }

  public JPanel getPanel() {
    return myTopPanel;
  }

  private void addLibrary() {
    EditLibraryDialog editLibDialog = new EditLibraryDialog();
    editLibDialog.show();
    if (editLibDialog.isOK()) {
      createLibrary(editLibDialog.getLibName());
      myModified = true;
    }
  }

  private void createLibrary(String name) {
    myLibTableModel.getLibraryTable().createLibrary(name);
    myLibraryTable.repaint();
  }

  public boolean isModified() {
    return myModified;
  }

  public void resetTable(LibraryTable libTable) {
    myLibTableModel.resetTable(libTable);
    myModified = false;
    myLibraryTable.repaint();
  }

}
