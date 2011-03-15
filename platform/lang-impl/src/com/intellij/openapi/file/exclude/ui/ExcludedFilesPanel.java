/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.file.exclude.ui;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class ExcludedFilesPanel {
  private JPanel myTopPanel;
  private JButton myPutBackButton;
  private JBTable myFileTable;

  private final FileTableModel myFileTableModel;

  public ExcludedFilesPanel(Collection<VirtualFile> files) {
    myFileTableModel = new FileTableModel(files);
    myFileTable.setModel(myFileTableModel);
    myFileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myFileTableModel.addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        updateSelection();
      }
    });

    myPutBackButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myFileTableModel.removeFile(myFileTable.getSelectedRow());
      }
    });

    updateSelection();
  }

  private void updateSelection() {
    if (myFileTableModel.getRowCount() == 0) {
      myPutBackButton.setEnabled(false);
    }
    else {
      myPutBackButton.setEnabled(true);
      myFileTable.setRowSelectionInterval(0, 0);
    }
  }

  public JPanel getTopJPanel() {
    return myTopPanel;
  }

  private static class FileTableModel extends AbstractTableModel {
    private List<VirtualFile> myExcludedFiles = new ArrayList<VirtualFile>();

    public FileTableModel(@Nullable Collection<VirtualFile> files) {
      if (files != null) {
        myExcludedFiles.addAll(files);
      }
    }

    @Override
    public int getRowCount() {
      return myExcludedFiles.size();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      VirtualFile file = myExcludedFiles.get(rowIndex);
      return file.getPath();
    }

    public void removeFile(int index) {
      if (index >= 0 && index < myExcludedFiles.size()) {
        myExcludedFiles.remove(index);
      }
      fireTableDataChanged();
    }

    public Collection<VirtualFile> getExcludedFiles() {
      return myExcludedFiles;
    }

    public void resetFiles(@Nullable Collection<VirtualFile> files) {
      myExcludedFiles.clear();
      if (files != null) {
        myExcludedFiles.addAll(files);
      }
      fireTableDataChanged();
    }
  }

  public Collection<VirtualFile> getExcludedFiles() {
    return myFileTableModel.getExcludedFiles();
  }

  public void resetFiles(Collection<VirtualFile> files) {
    myFileTableModel.resetFiles(files);
  }
}
