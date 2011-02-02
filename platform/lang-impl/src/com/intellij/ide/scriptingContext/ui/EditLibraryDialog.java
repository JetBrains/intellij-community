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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.scriptingContext.LangScriptingContextProvider;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryTable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

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
  private JButton myAddDocUrlButton;
  private JButton myRemoveDocUrlButton;
  private JBList myDocUrlList;
  private Project myProject;
  private FileTableModel myFileTableModel;
  private VirtualFile mySelectedFile;
  private LangScriptingContextProvider myProvider;
  private MyDocUrlListModel myDocUrlListModel;

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

    myFileTableModel = new FileTableModel();
    myFileTable.setRowHeight(myFileTable.getRowHeight() + 5);
    myFileTable.setModel(myFileTableModel);
    new TableSpeedSearch(myFileTable);

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

    myDocUrlListModel = new MyDocUrlListModel();
    myDocUrlList.setModel(myDocUrlListModel);
    myDocUrlListModel.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        myRemoveDocUrlButton.setEnabled(true);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        Object source = e.getSource();
        if (source instanceof MyDocUrlListModel && ((MyDocUrlListModel)source).getDocUrls().length == 0) {
          myRemoveDocUrlButton.setEnabled(false);
        }
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        // do nothing
      }
    });
    
    myAddDocUrlButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        specifyDocUrl();
      }
    });
    
    myRemoveDocUrlButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        removeDocUrl();
      }
    });
    myRemoveDocUrlButton.setEnabled(false);
    
    init();

    TableColumn typeCol = myFileTable.getColumnModel().getColumn(FILE_TYPE_COL);
    typeCol.setMaxWidth(80);
    MyTableCellEditor cellEditor = new MyTableCellEditor(new JComboBox(new String[] {mySourceTypeName, myCompactTypeName}), myFileTableModel);
    typeCol.setCellEditor(cellEditor);
    
    TableColumn fileCol = myFileTable.getColumnModel().getColumn(FILE_LOCATION_COL);
    fileCol.setCellRenderer(new MyTableCellRenderer());
  }

  public EditLibraryDialog(String title, LangScriptingContextProvider provider, Project project, ScriptingLibraryTable.LibraryModel lib) {
    this(title, provider, project);
    myLibName.setText(lib.getName());
    myFileTableModel.setFiles(lib.getSourceFiles(), lib.getCompactFiles());
    String[] docUrls = lib.getDocUrls(); 
    myDocUrlListModel.setDocUrls(docUrls);
    if (docUrls.length > 0) {
      myRemoveDocUrlButton.setEnabled(true);
    }
  }
  
  private class MyTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component renderer = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (renderer instanceof JLabel) {
        VirtualFile file = myFileTableModel.getFileAt(row);
        if (file != null) {
          ((JLabel)renderer).setToolTipText(file.getPresentableUrl());
        }
      }
      return renderer;
    }
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
      VirtualFile selectedFile = files[0];
      if (selectedFile.isValid()) {
        if (selectedFile.isDirectory()) {
          if (myLibName.getText().isEmpty()) myLibName.setText(selectedFile.getName());
          addRecursively(selectedFile);
        }
        else {
          addSingleFile(selectedFile, true);
        }
      }
    }
  }

  private void addRecursively(VirtualFile dir) {
    for (VirtualFile file : dir.getChildren()) {
      if (file.isValid()) {
        if (file.isDirectory()) {
          addRecursively(file);
        }
        else {
          if (myProvider.acceptsExtension(file.getExtension())) {
            addSingleFile(file, false);
          }
        }
      }
    }
  }
  
  private void addSingleFile(VirtualFile file, boolean scrollToAdded) {
    int index = myFileTableModel.addFile(file);
    myFileTable.setRowSelectionInterval(index, index);
    if (scrollToAdded) {
      Component parent = myFileTable;
      JScrollPane scrollPane = null;
      while ((parent = parent.getParent()) != null) {
        if (parent instanceof JScrollPane) {
          scrollPane = (JScrollPane)parent;
          break;
        }
      }
      if (scrollPane != null) {
        JViewport viewPort = scrollPane.getViewport();
        Point p = viewPort.getViewPosition();
        Rectangle r = myFileTable.getCellRect(index, 0, true);
        r.setLocation(r.x - p.x, r.y - p.y + myFileTable.getRowHeight());  
        viewPort.scrollRectToVisible(r);
      }
    }
  }
  
  private class LibFileChooserDescriptor extends FileChooserDescriptor {
    public LibFileChooserDescriptor() {
      super (true, true, false, true, false, false);
      setTitle(IdeBundle.message("scripting.lib.select.root"));
    }

    @Override
    public boolean isFileSelectable(VirtualFile file) {
      if (!file.isDirectory() && !myProvider.acceptsExtension(file.getExtension())) return false;
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
          return IdeBundle.message("scripting.lib.file.name");
        case FILE_TYPE_COL:
          return IdeBundle.message("scripting.lib.file.type");
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

    private SortedList<VirtualFile> myFiles = new SortedList<VirtualFile>(new Comparator<VirtualFile>() {
      @Override
      public int compare(VirtualFile file1, VirtualFile file2) {
        return file1.getName().compareTo(file2.getName());
      }
    });
    private HashSet<VirtualFile> myCompactFiles = new HashSet<VirtualFile>();

    public int addFile(VirtualFile file) {
      myFiles.add(file);
      if (myProvider.isCompact(file)) {
        myCompactFiles.add(file);
      }
      fireTableDataChanged();
      return myFiles.indexOf(file);
    }

    public void setFiles(Set<VirtualFile> sourceFiles, Set<VirtualFile> compactFiles) {
      myFiles.clear();
      myFiles.addAll(sourceFiles);
      myFiles.addAll(compactFiles);
      myCompactFiles.addAll(compactFiles);
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
          return file.getPresentableName();
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
  
  public String[] getDocUrls() {
    return myDocUrlListModel.getDocUrls();
  }

  @Override
  protected void doOKAction() {
    if (!isLibNameValid(myLibName.getText())) {
      Messages.showErrorDialog(myProject, IdeBundle.message("scripting.lib.invalid.name"), "Error");
      return;
    }
    super.doOKAction();
  }

  private static boolean isLibNameValid(String libName) {
    return libName != null && libName.matches("\\w[\\w\\d\\._\\-\\d]*");
  }
  
  private static class MyDocUrlListModel extends AbstractListModel {
    
    private ArrayList<String> myDocUrls = new ArrayList<String>();
    
    public void setDocUrls(String[] urls) {
      if (urls != null && urls.length > 0) {
        myDocUrls.addAll(Arrays.asList(urls));
      }
    }

    @Override
    public int getSize() {
      return myDocUrls.size();
    }

    @Override
    public Object getElementAt(int index) {
      return myDocUrls.get(index);
    }
    
    public int addUrl(String url) {
      myDocUrls.add(url);
      int newIndex = myDocUrls.indexOf(url);
      fireIntervalAdded(this, newIndex, newIndex);
      return newIndex;
    }
    
    public int indexOf(String url) {
      return myDocUrls.indexOf(url);
    }

    public void remove(String url) {
      if (url == null || !myDocUrls.contains(url)) return;
      int index = myDocUrls.indexOf(url);
      myDocUrls.remove(url);
      fireIntervalRemoved(this, index, index);
    }
    
    public boolean contains(String url) {
      return myDocUrls.contains(url);
    }
    
    public String[] getDocUrls() {
      return ArrayUtil.toStringArray(myDocUrls);
    }
  }
  
  private void specifyDocUrl() {
    String defaultUrl = findUnspecifiedMatchingDocUrl(myFileTableModel.getSourceFiles());
    if (defaultUrl == null) {
      defaultUrl = findUnspecifiedMatchingDocUrl(myFileTableModel.getCompactFiles());
    }
    VirtualFile vf = Util.showSpecifyJavadocUrlDialog(contentPane, defaultUrl != null ? defaultUrl : "");
    if (vf != null && vf.isValid()) {
      String url = vf.getUrl();
      int index = myDocUrlListModel.addUrl(url);
      myDocUrlList.ensureIndexIsVisible(index);
      myDocUrlList.setSelectedIndex(index);
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLibName;
  }

  @Nullable
  private String findUnspecifiedMatchingDocUrl(VirtualFile[] files) {
    String docUrl;
    for (VirtualFile file : files) {
      docUrl = myProvider.getDefaultDocUrl(file);
      if (docUrl != null && !myDocUrlListModel.contains(docUrl)) return docUrl;
    }
    return null;
  }
  
  private void removeDocUrl() {
    myDocUrlListModel.remove((String)myDocUrlList.getSelectedValue());
  }
}
