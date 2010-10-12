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

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryTableModel extends AbstractTableModel {

  private static final int LIB_NAME_COL = 0;

  private LibraryTable myLibTable;
  private LibraryTable.ModifiableModel myLibTableModel;
  private boolean myTableChanged;

  public ScriptingLibraryTableModel(LibraryTable libTable) {
    myLibTable = libTable;
    myLibTableModel = libTable.getModifiableModel();
    myTableChanged = false;
  }

  public void resetTable(LibraryTable libTable) {
    myLibTable = libTable;
    myTableChanged = false;
    fireTableDataChanged();
  }

  @Override
  public int getRowCount() {
    if (myLibTable != null) {
      return myLibTable.getLibraries().length;
    }
    return 0;
  }

  @Override
  public int getColumnCount() {
    return 1;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    if (columnIndex == LIB_NAME_COL) {
      return myLibTable.getLibraries()[rowIndex].getName();
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

  public void createLibrary(String name, VirtualFile[] files) {
    Library lib = myLibTable.createLibrary(name);
    Library.ModifiableModel libModel = lib.getModifiableModel();
    for (VirtualFile file : files) {
      libModel.addRoot(file, OrderRootType.CLASSES);
    }
    libModel.commit();
    myLibTableModel.commit();
    fireLibTableChanged();
  }

  @Nullable
  public Library getLibrary(String name) {
    return myLibTable == null ? null : myLibTable.getLibraryByName(name);
  }

  public void removeLibrary(String name) {
    Library libToRemove = myLibTable.getLibraryByName(name);
    if (libToRemove != null) {
      myLibTable.removeLibrary(libToRemove);
      fireLibTableChanged();
    }
  }

  public void fireLibTableChanged() {
    myTableChanged = true;
    fireTableDataChanged();
  }

  @Nullable
  public String getLibNameAt(int row) {
    Library[] libs = myLibTable.getLibraries();
    if (row < 0 || row > libs.length - 1) return null;
    return libs[row].getName();
  }

  public boolean isChanged() {
    return myTableChanged;
  }

}
