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
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryTableModel extends AbstractTableModel {

  private static final int LIB_NAME_COL = 0;

  //private LibraryTable myLibTable;
  private TypedLibraryTableWrapper myTableWrapper;
  private ScriptingLibraryManager myLibraryManager;

  public ScriptingLibraryTableModel(ScriptingLibraryManager libManager) {
    myTableWrapper = new TypedLibraryTableWrapper(libManager);
    myLibraryManager = libManager;
  }

  public void resetTable() {
    myTableWrapper = new TypedLibraryTableWrapper(myLibraryManager);
    fireTableDataChanged();
  }

  @Override
  public int getRowCount() {
    if (myTableWrapper != null) {
      return myTableWrapper.getLibCount();
    }
    return 0;
  }

  @Override
  public int getColumnCount() {
    return 1;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Library lib = myTableWrapper.getLibraryAt(rowIndex);
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

  public void createLibrary(final String name, final VirtualFile[] files) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Library lib = myLibraryManager.createLibrary(name);
        if (lib != null) {
          Library.ModifiableModel libModel = lib.getModifiableModel();
          for (VirtualFile file : files) {
            libModel.addRoot(file, OrderRootType.SOURCES);
          }
          libModel.commit();
          fireLibTableChanged();
        }
      }
    });
  }

  @Nullable
  public Library getLibrary(String name) {
    return myTableWrapper == null ? null : myTableWrapper.getLibraryByName(name);
  }

  public void removeLibrary(String name) {
    Library libToRemove = myTableWrapper.getLibraryByName(name);
    if (libToRemove != null) {
      myLibraryManager.removeLibrary(libToRemove);
      fireLibTableChanged();
    }
  }

  public void fireLibTableChanged() {
    myTableWrapper.update();
    fireTableDataChanged();
  }

  @Nullable
  public String getLibNameAt(int row) {
    Library lib = myTableWrapper.getLibraryAt(row);
    return lib != null ? lib.getName() : null;
  }

  public boolean isChanged() {
    return myTableWrapper.isUpdated();
  }

}
