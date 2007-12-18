package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Combobox items are Documents for this combobox
 * @author max
 */
public class EditorComboBoxEditor implements ComboBoxEditor{
  private final EditorTextField myTextField;
  @NonNls protected static final String NAME = "ComboBox.textField";

  public EditorComboBoxEditor(Project project, FileType fileType) {
    myTextField = new EditorTextField((Document)null, project, fileType);
    myTextField.setName(NAME);
  }

  public void selectAll() {
    myTextField.selectAll();
    myTextField.requestFocus();
  }

  public Editor getEditor() {
    return myTextField.getEditor();
  }

  public Component getEditorComponent() {
    return myTextField;
  }

  public void addActionListener(ActionListener l) {

  }

  public void removeActionListener(ActionListener l) {

  }

  public Object getItem() {
    return myTextField.getDocument();
  }

  public void setItem(Object anObject) {
    myTextField.setDocument((Document)anObject);
  }
}
