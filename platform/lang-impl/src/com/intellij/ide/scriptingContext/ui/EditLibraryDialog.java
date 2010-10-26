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
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryTable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;

public class EditLibraryDialog extends DialogWrapper {
  private JPanel contentPane;
  private JTextField myLibName;
  private JButton myAddFileButton;
  private JButton myRemoveFileButton;
  private JBTable myFileTable;
  private Project myProject;
  private FileTableModel myFileTableModel;
  private VirtualFile mySelectedFile;
  private LangScriptingContextProvider myProvider;

  public EditLibraryDialog(String title, LangScriptingContextProvider provider, Project project) {
    super(true);
    setTitle(title);
    myProvider = provider;
    myAddFileButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        addFiles();
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
  }

  public EditLibraryDialog(String title, LangScriptingContextProvider provider, Project project, ScriptingLibraryTable.LibraryModel lib) {
    this(title, provider, project);
    myLibName.setText(lib.getName());
    myFileTableModel.setFiles(lib.getSourceFiles());
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

  private class LibFileChooserDescriptor extends FileChooserDescriptor {
    public LibFileChooserDescriptor() {
      super (true, false, false, true, false, false);
      setTitle("Select library file"); // TODO: Add a resources string
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

  private static class FileTableModel extends AbstractTableModel {

    private static final int FILE_COLUMN = 0;

    @Override
    public String getColumnName(int column) {
      if (column == FILE_COLUMN) {
        return "Path";
      }
      return "";
    }

    private ArrayList<VirtualFile> myFiles = new ArrayList<VirtualFile>();

    public void addFile(VirtualFile file) {
      myFiles.add(file);
      fireTableDataChanged();
    }

    public void setFiles(VirtualFile[] files) {
      myFiles.clear();
      myFiles.addAll(Arrays.asList(files));
    }

    @Override
    public int getRowCount() {
      return myFiles.size();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == FILE_COLUMN) {
        return myFiles.get(rowIndex);
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

    public VirtualFile[] getFiles() {
      return myFiles.toArray(new VirtualFile[myFiles.size()]);
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

  public VirtualFile[] getFiles() {
    return myFileTableModel.getFiles();
  }

  @Override
  protected void doOKAction() {
    if (!isLibNameValid(myLibName.getText())) {
      Messages.showErrorDialog(myProject, "Invalid library name", "Error");
      return;
    }
    super.doOKAction();
  }

  private static boolean isLibNameValid(String libName) {
    return libName != null && libName.matches("\\w[\\w\\d\\._\\-\\d]*");
  }
}
