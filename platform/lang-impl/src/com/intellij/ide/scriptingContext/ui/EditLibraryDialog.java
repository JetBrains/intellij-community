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

import com.intellij.ide.scriptingContext.LangScriptingContextProvider;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryTable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;

public class EditLibraryDialog extends DialogWrapper {

  private static final int FILE_LOCATION_COL  = 0;
  private static final int FILE_TYPE_COL = 1;

  private final String mySourceTypeName;
  private final String myCompactTypeName;

  private JPanel contentPane;
  private JTextField myLibName;
  private JButton myAddFileButton;
  private JButton myRemoveFileButton;
  private JBTable myFileTable;
  private JButton myAttachFromButton;
  private Project myProject;
  private FileTableModel myFileTableModel;
  private VirtualFile mySelectedFile;
  private LangScriptingContextProvider myProvider;

  public EditLibraryDialog(String title, LangScriptingContextProvider provider, Project project) {
    super(true);
    setTitle(title);
    myProvider = provider;
    mySourceTypeName = provider.getLibraryTypeName(OrderRootType.SOURCES);
    myCompactTypeName = provider.getLibraryTypeName(OrderRootType.CLASSES);
    myAddFileButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        addFiles();
      }
    });

    myAttachFromButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        attachFromDirectory();
      }
    });

    myFileTableModel = new FileTableModel();
    myFileTable.setModel(myFileTableModel);

    myRemoveFileButton.setEnabled(false);
    myRemoveFileButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        removeSelected();
      }
    });
    myProject = project;
    myFileTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateSelection();
      }
    });
    init();

    TableColumn typeCol = myFileTable.getColumnModel().getColumn(FILE_TYPE_COL);
    typeCol.setMaxWidth(80);
    MyTableCellEditor cellEditor = new MyTableCellEditor(new JComboBox(new String[] {mySourceTypeName, myCompactTypeName}), myFileTableModel);
    typeCol.setCellEditor(cellEditor);
  }

  public EditLibraryDialog(String title, LangScriptingContextProvider provider, Project project, ScriptingLibraryTable.LibraryModel lib) {
    this(title, provider, project);
    myLibName.setText(lib.getName());
    myFileTableModel.setFiles(lib.getSourceFiles(), lib.getCompactFiles());
  }

  private class MyTableCellEditor extends DefaultCellEditor {

    private FileTableModel myFileTableModel;
    private VirtualFile myFile;

    public MyTableCellEditor(JComboBox comboBox, FileTableModel fileTableModel) {
      super(comboBox);
      myFileTableModel = fileTableModel;
    }

    @Override
    public boolean stopCellEditing() {
      if (myFile != null) {
        Object value = getCellEditorValue();
        myFileTableModel.setFileType(myFile, value.equals(myCompactTypeName));
      }
      return super.stopCellEditing();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      myFile = myFileTableModel.getFileAt(row);
      return super.getTableCellEditorComponent(table, value, isSelected, row, column);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public String getLibName() {
    return myLibName.getText();
  }

  private void addFiles() {
    FileChooserDescriptor chooserDescriptor = new LibFileChooserDescriptor();
    VirtualFile[] files = FileChooser.chooseFiles(myProject, chooserDescriptor);
    if (files.length == 1 && files[0] != null) {
      myFileTableModel.addFile(files[0]);
    }
  }

  private void attachFromDirectory() {
    FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    chooserDescriptor.setTitle("Select a directory to attach files from");  //TODO<rv> Move to resources
    VirtualFile[] files = FileChooser.chooseFiles(myProject, chooserDescriptor);
    if (files.length == 1 && files[0] != null) {
      VirtualFile chosenDir  = files[0];
      if (chosenDir.isDirectory() && chosenDir.isValid()) {
        if (myLibName.getText().isEmpty()) myLibName.setText(chosenDir.getName());
        for (VirtualFile file : chosenDir.getChildren()) {
          if (file.isValid() && !file.isDirectory() && myProvider.acceptsExtension(file.getExtension())) {
            myFileTableModel.addFile(file);
          }
        }
      }
    }
  }

  private class LibFileChooserDescriptor extends FileChooserDescriptor {
    public LibFileChooserDescriptor() {
      super (true, false, false, true, false, false);
      setTitle("Select library file"); //TODO<rv> Move to resources
    }

    @Override
    public boolean isFileSelectable(VirtualFile file) {
      if (!myProvider.acceptsExtension(file.getExtension())) return false;
      return super.isFileSelectable(file);
    }

    @Override
    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      if (!file.isDirectory() && !myProvider.acceptsExtension(file.getExtension())) return false;
      return super.isFileVisible(file, showHiddenFiles);
    }
  }

  private class FileTableModel extends AbstractTableModel {

    @Override
    public String getColumnName(int column) {
      switch(column) {
        case FILE_LOCATION_COL:
          return "Location"; //TODO<rv> Move to resources
        case FILE_TYPE_COL: //TODO<rv> Move to resources
          return "Type";
      }
      return "";
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (columnIndex == FILE_TYPE_COL) {
        return true;
      }
      return super.isCellEditable(rowIndex, columnIndex);
    }

    private ArrayList<VirtualFile> myFiles = new ArrayList<VirtualFile>();
    private HashSet<VirtualFile> myCompactFiles = new HashSet<VirtualFile>();

    public void addFile(VirtualFile file) {
      myFiles.add(file);
      if (myProvider.isCompact(file)) {
        myCompactFiles.add(file);
      }
      fireTableDataChanged();
    }

    public void setFiles(VirtualFile[] sourceFiles, VirtualFile[] compactFiles) {
      myFiles.clear();
      myFiles.addAll(Arrays.asList(sourceFiles));
      myFiles.addAll(Arrays.asList(compactFiles));
      myCompactFiles.addAll(Arrays.asList(compactFiles));
    }

    public void setFileType(VirtualFile file, boolean isCompact) {
      if (isCompact) {
        if (!myCompactFiles.contains(file)) {
          myCompactFiles.add(file);
        }
      }
      else {
        if (myCompactFiles.contains(file)) {
          myCompactFiles.remove(file);
        }
      }
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return myFiles.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      VirtualFile file = myFiles.get(rowIndex);
      switch (columnIndex) {
        case FILE_LOCATION_COL:
          return file;
        case FILE_TYPE_COL:
          return myCompactFiles.contains(file) ? myCompactTypeName : mySourceTypeName;
      }
      return "";
    }

    @Nullable
    public VirtualFile getFileAt(int row) {
      if (row < 0 || row >= myFiles.size()) return null;
      return myFiles.get(row);
    }

    public void removeFile(VirtualFile file) {
      if (myFiles.remove(file)) {
        fireTableDataChanged();
      }
    }

    public VirtualFile[] getSourceFiles() {
      ArrayList<VirtualFile> sourceFiles = new ArrayList<VirtualFile>();
      for (VirtualFile file : myFiles) {
        if (!myCompactFiles.contains(file)) {
          sourceFiles.add(file);
        }
      }
      return sourceFiles.toArray(new VirtualFile[sourceFiles.size()]);
    }

    public VirtualFile[] getCompactFiles() {
      return myCompactFiles.toArray(new VirtualFile[myCompactFiles.size()]);
    }
  }

  private void updateSelection() {
    int selectedRow = myFileTable.getSelectedRow();
    if (selectedRow >= 0) {
      mySelectedFile = myFileTableModel.getFileAt(selectedRow);
    }
    else {
      mySelectedFile = null;
    }
    myRemoveFileButton.setEnabled(mySelectedFile != null);
  }

  private void removeSelected() {
     myFileTableModel.removeFile(mySelectedFile);
  }

  public VirtualFile[] getSourceFiles() {
    return myFileTableModel.getSourceFiles();
  }

  public VirtualFile[] getCompactFiles() {
    return myFileTableModel.getCompactFiles();
  }

  @Override
  protected void doOKAction() {
    if (!isLibNameValid(myLibName.getText())) {
      Messages.showErrorDialog(myProject, "Invalid library name", "Error"); //TODO<rv> Move to resources
      return;
    }
    super.doOKAction();
  }

  private static boolean isLibNameValid(String libName) {
    return libName != null && libName.matches("\\w[\\w\\d\\._\\-\\d]*");
  }
}
