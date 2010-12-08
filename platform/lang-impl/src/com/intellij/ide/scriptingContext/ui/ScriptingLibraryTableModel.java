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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryManager;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryTableModel extends AbstractTableModel {

  private static final int LIB_NAME_COL = 0;


  private ScriptingLibraryManager myLibraryManager;
  private ScriptingLibraryTable myLibraryTable;
  private boolean myIsChanged;

  public ScriptingLibraryTableModel(ScriptingLibraryManager libManager) {
    myLibraryTable = libManager.getScriptingLibraryTable();
    myLibraryManager = libManager;
    myIsChanged = false;
  }

  public void resetTable() {
    myLibraryTable = myLibraryManager.getScriptingLibraryTable();
    myIsChanged = false;
    fireTableDataChanged();
  }

  @Override
  public int getRowCount() {
    return myLibraryTable.getLibCount();
  }

  @Override
  public int getColumnCount() {
    return 1;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    ScriptingLibraryTable.LibraryModel lib = myLibraryTable.getLibraryAt(rowIndex);
    assert lib != null;
    if (columnIndex == LIB_NAME_COL) {
      return lib.getName();
    }
    return "?";
  }

  @Override
  public String getColumnName(int column) {
    if (column == LIB_NAME_COL) {
      return "Name";
    }
    return "?";
  }

  public void createLibrary(final String name, final VirtualFile[] sourceFiles, final VirtualFile[] compactFiles, final String[] docUrls) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ScriptingLibraryTable.LibraryModel lib = myLibraryManager.createLibrary(name, sourceFiles, compactFiles, docUrls);
        if (lib != null) {
          fireLibTableChanged();
        }
      }
    });
  }

  @Nullable
  public ScriptingLibraryTable.LibraryModel getLibrary(String name) {
    return myLibraryTable.getLibraryByName(name);
  }

  public void removeLibrary(String name) {
    ScriptingLibraryTable.LibraryModel libToRemove = myLibraryTable.getLibraryByName(name);
    if (libToRemove != null) {
      myLibraryManager.removeLibrary(libToRemove);
      fireLibTableChanged();
    }
  }

  public void updateLibrary(final String oldName,
                            final String name,
                            final VirtualFile[] sourceFiles,
                            final VirtualFile[] compactFiles,
                            final String[] docUrls) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myLibraryManager.updateLibrary(oldName, name, sourceFiles, compactFiles, docUrls);
        fireLibTableChanged();
      }
    });
  }

  public void fireLibTableChanged() {
    myIsChanged = true;
    fireTableDataChanged();
  }

  @Nullable
  public String getLibNameAt(int row) {
    ScriptingLibraryTable.LibraryModel lib = myLibraryTable.getLibraryAt(row);
    return lib != null ? lib.getName() : null;
  }

  public boolean isChanged() {
    return myIsChanged;
  }

}
