/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteOverridingMethodUsageInfo;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.Table;
import com.intellij.usageView.UsageInfo;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
class OverridingMethodsDialog extends DialogWrapper {
  private final List<UsageInfo> myOverridingMethods;
  private final String[] myMethodText;
  private final boolean[] myChecked;

  private static final int CHECK_COLUMN = 0;
  private Table myTable;

  public OverridingMethodsDialog(Project project, List<UsageInfo> overridingMethods) {
    super(project, true);
    myOverridingMethods = overridingMethods;
    myChecked = new boolean[myOverridingMethods.size()];
    for (int i = 0; i < myChecked.length; i++) {
      myChecked[i] = true;
    }

    myMethodText = new String[myOverridingMethods.size()];
    for (int i = 0; i < myMethodText.length; i++) {
      myMethodText[i] = PsiFormatUtil.formatMethod(
              ((SafeDeleteOverridingMethodUsageInfo) myOverridingMethods.get(i)).getOverridingMethod(),
              PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_CONTAINING_CLASS
                                    | PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_TYPE,
              PsiFormatUtil.SHOW_TYPE
      );
    }

    setTitle(RefactoringBundle.message("unused.overriding.methods.title"));
    init();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.safeDelete.OverridingMethodsDialog";
  }

  public ArrayList<UsageInfo> getSelected() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    for (int i = 0; i < myChecked.length; i++) {
      if(myChecked[i]) {
        result.add(myOverridingMethods.get(i));
      }
    }
    return result;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()/*, getHelpAction()*/};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.SAFE_DELETE_OVERRIDING);
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(new JLabel(RefactoringBundle.message("there.are.unused.methods.that.override.methods.you.delete")));
    panel.add(new JLabel(RefactoringBundle.message("choose.the.ones.you.want.to.be.deleted")));
    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
    final MyTableModel tableModel = new MyTableModel();
    myTable = new Table(tableModel);
    myTable.setShowGrid(false);

    TableColumnModel columnModel = myTable.getColumnModel();
//    columnModel.getColumn(DISPLAY_NAME_COLUMN).setCellRenderer(new MemberSelectionTable.MyTableRenderer());
    final int checkBoxWidth = new JCheckBox().getPreferredSize().width;
    columnModel.getColumn(CHECK_COLUMN).setCellRenderer(new BooleanTableCellRenderer());
    columnModel.getColumn(CHECK_COLUMN).setMaxWidth(checkBoxWidth);
    columnModel.getColumn(CHECK_COLUMN).setMinWidth(checkBoxWidth);


    // make SPACE check/uncheck selected rows
    @NonNls InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    @NonNls final ActionMap actionMap = myTable.getActionMap();
    actionMap.put("enable_disable", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        int[] rows = myTable.getSelectedRows();
        if (rows.length > 0) {
          boolean valueToBeSet = false;
          for (int row : rows) {
            if (!myChecked[row]) {
              valueToBeSet = true;
              break;
            }
          }
          for (int row : rows) {
            myChecked[row] = valueToBeSet;
          }

          tableModel.updateData();
        }
      }
    });



    /*Border titledBorder = BorderFactory.createTitledBorder("Select methods");
    Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    panel.setBorder(border);*/
    panel.setLayout(new BorderLayout());

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);

    panel.add(scrollPane, BorderLayout.CENTER);
    return panel;
  }

  class MyTableModel extends AbstractTableModel {
    public int getRowCount() {
      return myChecked.length;
    }

    public String getColumnName(int column) {
      switch(column) {
        case CHECK_COLUMN:
          return " ";
        default:
          return RefactoringBundle.message("method.column");
      }
    }

    public Class getColumnClass(int columnIndex) {
      switch(columnIndex) {
        case CHECK_COLUMN:
          return Boolean.class;
        default:
          return String.class;
      }
    }


    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      if(columnIndex == CHECK_COLUMN) {
        return Boolean.valueOf(myChecked[rowIndex]);
      }
      else {
        return myMethodText[rowIndex];
      }
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if(columnIndex == CHECK_COLUMN) {
        myChecked[rowIndex] = ((Boolean) aValue).booleanValue();
      }
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == CHECK_COLUMN;
    }

    void updateData() {
      fireTableDataChanged();
    }
  }
}
