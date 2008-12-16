package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.ClassFilterEditor;

/**
 * User: lex
 * Date: Aug 29, 2003
 * Time: 2:38:30 PM
 */
public class InstanceFilterEditor extends ClassFilterEditor {
  public InstanceFilterEditor(Project project) {
    super(project);
    myAddPatternButton.setVisible(false);
  }

  protected void addClassFilter() {
    String idString = Messages.showInputDialog(myProject, DebuggerBundle.message("add.instance.filter.dialog.prompt"), DebuggerBundle.message("add.instance.filter.dialog.title"), Messages.getQuestionIcon());
    if (idString != null) {
      ClassFilter filter = createFilter(idString);
      if(filter != null){
        myTableModel.addRow(filter);
        int row = myTableModel.getRowCount() - 1;
        myTable.getSelectionModel().setSelectionInterval(row, row);
        myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

      }
      myTable.requestFocus();
    }
  }

  protected String getAddButtonText() {
    return DebuggerBundle.message("button.add");
  }

  protected ClassFilter createFilter(String pattern) {
    try {
      Long.parseLong(pattern);
      return super.createFilter(pattern);
    } catch (NumberFormatException e) {
      Messages.showMessageDialog(this, DebuggerBundle.message("add.instance.filter.dialog.error.numeric.value.expected"), DebuggerBundle.message("add.instance.filter.dialog.title"), Messages.getErrorIcon());
      return null;
    }
  }
}
