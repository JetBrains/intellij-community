// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.settings.mappings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFile;
import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.JBUI;
import com.jetbrains.jsonSchema.JsonMappingKind;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

class JsonMappingsTableCellEditor extends AbstractTableCellEditor {
  final TextFieldWithBrowseButton myComponent;
  final JPanel myWrapper;

  private final UserDefinedJsonSchemaConfiguration.Item myItem;
  private final Project myProject;
  private final TreeUpdater myTreeUpdater;

  JsonMappingsTableCellEditor(UserDefinedJsonSchemaConfiguration.Item item, Project project, TreeUpdater treeUpdater) {
    myItem = item;
    myProject = project;
    myTreeUpdater = treeUpdater;
    myComponent = new TextFieldWithBrowseButton() {
      @Override
      protected void installPathCompletion(FileChooserDescriptor fileChooserDescriptor, Disposable parent) {
        // do nothing
      }
    };
    myWrapper = new JPanel();
    myWrapper.setBorder(JBUI.Borders.empty(-3, 0));
    myWrapper.setLayout(new BorderLayout());
    JLabel label = new JLabel(item.mappingKind.getPrefix().trim(), item.mappingKind.getIcon(), SwingConstants.LEFT);
    label.setBorder(JBUI.Borders.emptyLeft(1));
    myWrapper.add(label,
                  BorderLayout.LINE_START);
    myWrapper.add(myComponent,
                  BorderLayout.CENTER);
    FileChooserDescriptor descriptor = createDescriptor(item);
    if (item.isPattern()) {
      myComponent.getButton().setVisible(false);
    }
    else {
      myComponent.addBrowseFolderListener(
        new TextBrowseFolderListener(
          descriptor, myProject) {
          @Override
          protected @NotNull String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
            String relativePath = VfsUtilCore.getRelativePath(chosenFile, myProject.getBaseDir());
            return relativePath != null ? relativePath : chosenFile.getPath();
          }
        });
    }

    FileTextFieldImpl field = null;
    if (!item.isPattern() && !ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      LocalFsFinder finder = new LocalFsFinder();
      finder.setBaseDir(new File(myProject.getBaseDir().getPath()));
      field = new MyFileTextFieldImpl(finder, descriptor, myComponent.getTextField(), myProject, myComponent);
    }

    // avoid closing the dialog by [Enter]
    FileTextFieldImpl finalField = field;
    myComponent.getTextField().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && (finalField == null || !finalField.isPopupDisplayed())) {
          stopCellEditing();
        }
      }
    });
  }

  private static @NotNull FileChooserDescriptor createDescriptor(UserDefinedJsonSchemaConfiguration.Item item) {
    return item.mappingKind == JsonMappingKind.File
           ? FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
           : FileChooserDescriptorFactory.createSingleFolderDescriptor();
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    myComponent.getChildComponent().setText(myItem.getPath());
    return myWrapper;
  }

  @Override
  public boolean stopCellEditing() {
    myItem.setPath(myComponent.getChildComponent().getText());
    myTreeUpdater.updateTree(true);
    return super.stopCellEditing();
  }

  @Override
  public Object getCellEditorValue() {
    return myComponent.getChildComponent().getText();
  }

  private static class MyFileTextFieldImpl extends FileTextFieldImpl {
    private final JTextField myTextField;
    private final Project myProject;

    MyFileTextFieldImpl(LocalFsFinder finder, FileChooserDescriptor descriptor, JTextField textField, Project project, Disposable parent) {
      super(textField, finder, new LocalFsFinder.FileChooserFilter(descriptor, true), FileChooserFactoryImpl.getMacroMap(), parent);
      myTextField = textField;
      myProject = project;
      myAutopopup = true;
    }

    @Override
    protected void setTextToFile(LookupFile file) {
      String path = file.getAbsolutePath();
      VirtualFile ioFile = VfsUtil.findFileByIoFile(new File(path), false);
      if (ioFile == null) {
        myTextField.setText(path);
        return;
      }
      String relativePath = VfsUtilCore.getRelativePath(ioFile, myProject.getBaseDir());
      myTextField.setText(relativePath != null ? relativePath : path);
    }
  }
}
