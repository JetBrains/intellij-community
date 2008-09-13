/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 7, 2007
 * Time: 1:45:27 PM
 */
package com.intellij.find.impl;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.StringComboboxEditor;

import javax.swing.*;

public class RevealingSpaceComboboxEditor extends StringComboboxEditor {
  public RevealingSpaceComboboxEditor(final Project project, ComboBox comboBox) {
    super(project, FileTypes.PLAIN_TEXT, comboBox);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        getEditor().getSettings().setWhitespacesShown(true);
      }
    });
  }

  public void setItem(Object anObject) {
    super.setItem(anObject);
    if (getEditor() != null) {
      getEditor().getSettings().setWhitespacesShown(true);
    }
  }
}