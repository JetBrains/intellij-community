package com.intellij.ide.structureView;

import com.intellij.openapi.fileEditor.FileEditor;

import javax.swing.*;

public interface StructureView{

  FileEditor getFileEditor();

  boolean navigateToSelectedElement(boolean requestFocus);

  JComponent getComponent();

  void dispose();

  void centerSelectedRow();

  void restoreState();

  void storeState();
}