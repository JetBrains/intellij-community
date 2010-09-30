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

import com.intellij.ide.scriptingContext.ScriptingLibraryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;

import javax.swing.table.AbstractTableModel;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryTableModel extends AbstractTableModel {

  private static final int LIB_NAME_COL = 0;

  private LibraryTable myLibTable;

  public ScriptingLibraryTableModel(Project project) {
    myLibTable = ScriptingLibraryManager.getLibraryTable(project);
  }

  @Override
  public int getRowCount() {
    return myLibTable.getLibraries().length;
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
}
