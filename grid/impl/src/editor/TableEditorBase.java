package com.intellij.database.editor;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author gregsh
 */
public abstract class TableEditorBase extends UserDataHolderBase implements FileEditor {

  private final Project myProject;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private JComponent myComponent;

  protected TableEditorBase(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  public abstract @NotNull DataGrid getDataGrid();

  @Override
  public @NotNull String getName() {
    return DataGridBundle.message("table.file.editor.name");
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  @Override
  public @NotNull DatabaseTableEditorLocation getCurrentLocation() {
    //TODO provide coordinates
    return new DatabaseTableEditorLocation(this, -1, -1);
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    //return StructureViewBuilder.PROVIDER.getStructureViewBuilder(myFile.getFileType(), myFile, myProject);
    return new TreeBasedStructureViewBuilder() {
      @Override
      public boolean isRootNodeShown() {
        return false;
      }

      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new TableEditorStructureViewModel(TableEditorBase.this);
      }
    };
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void dispose() {
  }

  @Override
  public @NotNull JComponent getComponent() {
    if (myComponent == null) {
      myComponent = UiDataProvider.wrapComponent(
        getDataGrid().getPanel().getComponent(), sink -> uiDataSnapshot(sink));
    }
    return myComponent;
  }

  protected void uiDataSnapshot(@NotNull DataSink sink) {
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return getDataGrid().getPreferredFocusedComponent();
  }
}
