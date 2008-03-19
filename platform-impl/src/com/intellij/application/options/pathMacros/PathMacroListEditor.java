package com.intellij.application.options.pathMacros;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;

/**
 * @author dsl
 */
public class PathMacroListEditor {
  JPanel myPanel;
  JButton myAddButton;
  JButton myRemoveButton;
  JButton myEditButton;
  JScrollPane myScrollPane;
  private JTextArea myDescriptionArea;
  private PathMacroTable myPathMacroTable;

  public PathMacroListEditor() {
    this(null, false);
  }

  public PathMacroListEditor(String[] undefinedMacroNames, boolean editOnlyPathsMode) {
    myPathMacroTable = undefinedMacroNames != null ? new PathMacroTable(undefinedMacroNames, editOnlyPathsMode) : new PathMacroTable();
    myScrollPane.setViewportView(myPathMacroTable);
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.addMacro();
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.removeSelectedMacros();
      }
    });
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.editMacro();
      }
    });
    myPathMacroTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateControls();
      }
    });

    updateControls();
  }

  private void updateControls() {
    myAddButton.setEnabled(myPathMacroTable.isAddEnabled());
    myRemoveButton.setEnabled(myPathMacroTable.isRemoveEnabled());
    myEditButton.setEnabled(myPathMacroTable.isEditEnabled());

    final int row = myPathMacroTable.getSelectedRow();
    if (row != -1) {
      final String description = myPathMacroTable.getMacroDescriptionAt(row);
      myDescriptionArea
          .setText((description == null || description.length() == 0)
                   ? ProjectBundle.message("project.configure.path.variables.no.description.text") : description);
    } else {
      myDescriptionArea.setText("");
    }
  }

  public void commit() throws ConfigurationException {
    final int count = myPathMacroTable.getRowCount();
    for (int idx = 0; idx < count; idx++) {
      String value = myPathMacroTable.getMacroValueAt(idx);
      if (value == null || value.length() == 0) {
        throw new ConfigurationException(
            ApplicationBundle.message("error.path.variable.is.undefined", myPathMacroTable.getMacroNameAt(idx)));
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myPathMacroTable.commit();
      }
    });
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void reset() {
    myPathMacroTable.reset();
  }

  public boolean isModified() {
    return myPathMacroTable.isModified();
  }

  private void createUIComponents() {
    myDescriptionArea = new JTextArea();
    myDescriptionArea.setEditable(false);
    myDescriptionArea.setPreferredSize(new Dimension(300, 100));
    myDescriptionArea.setOpaque(false);
  }
}
