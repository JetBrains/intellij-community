// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.safeDelete;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteOverridingMethodUsageInfo;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsagePreviewPanel;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author dsl
 */
class OverridingMethodsDialog extends DialogWrapper {
  private final List<? extends UsageInfo> myOverridingMethods;
  private final String[] myMethodText;
  private final boolean[] myChecked;

  private static final int CHECK_COLUMN = 0;
  private JBTable myTable;
   private final UsagePreviewPanel myUsagePreviewPanel;

  OverridingMethodsDialog(Project project, List<? extends UsageInfo> overridingMethods) {
    super(project, true);
    myOverridingMethods = overridingMethods;
    myChecked = new boolean[myOverridingMethods.size()];
    Arrays.fill(myChecked, true);

    myMethodText = new String[myOverridingMethods.size()];
    for (int i = 0; i < myMethodText.length; i++) {
      myMethodText[i] = PsiFormatUtil.formatMethod(
              ((SafeDeleteOverridingMethodUsageInfo) myOverridingMethods.get(i)).getOverridingMethod(),
              PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS
                                    | PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_TYPE,
              PsiFormatUtilBase.SHOW_TYPE
      );
    }
    myUsagePreviewPanel = new UsagePreviewPanel(project, new UsageViewPresentation());
    setTitle(JavaRefactoringBundle.message("unused.overriding.methods.title"));
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.safeDelete.OverridingMethodsDialog";
  }

  public ArrayList<UsageInfo> getSelected() {
    ArrayList<UsageInfo> result = new ArrayList<>();
    for (int i = 0; i < myChecked.length; i++) {
      if(myChecked[i]) {
        result.add(myOverridingMethods.get(i));
      }
    }
    return result;
  }

  @Override
  protected String getHelpId() {
    return HelpID.SAFE_DELETE_OVERRIDING;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(new JLabel(JavaRefactoringBundle.message("there.are.unused.methods.that.override.methods.you.delete")));
    panel.add(new JLabel(JavaRefactoringBundle.message("choose.the.ones.you.want.to.be.deleted")));
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  @Override
  protected void dispose() {
    Disposer.dispose(myUsagePreviewPanel);
    super.dispose();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
    final MyTableModel tableModel = new MyTableModel();
    myTable = new JBTable(tableModel);
    myTable.setShowGrid(false);

    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn checkboxColumn = columnModel.getColumn(CHECK_COLUMN);
    TableUtil.setupCheckboxColumn(checkboxColumn);
    checkboxColumn.setCellRenderer(new BooleanTableCellRenderer());

    // make SPACE check/uncheck selected rows
    @NonNls InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    @NonNls final ActionMap actionMap = myTable.getActionMap();
    actionMap.put("enable_disable", new AbstractAction() {
      @Override
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



    /*Border titledBorder = IdeBorderFactory.createBoldTitledBorder("Select methods");
    Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    panel.setBorder(border);*/
    panel.setLayout(new BorderLayout());

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);

    panel.add(scrollPane, BorderLayout.CENTER);
    ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        int index = myTable.getSelectionModel().getLeadSelectionIndex();
        if (index != -1) {
          UsageInfo usageInfo = myOverridingMethods.get(index);
          myUsagePreviewPanel.updateLayout(Collections.singletonList(usageInfo));
        }
        else {
          myUsagePreviewPanel.updateLayout(null);
        }
      }
    };
    myTable.getSelectionModel().addListSelectionListener(selectionListener);

    final Splitter splitter = new Splitter(true, 0.3f);
    splitter.setFirstComponent(panel);
    splitter.setSecondComponent(myUsagePreviewPanel);
    myUsagePreviewPanel.updateLayout(null);

    Disposer.register(myDisposable, new Disposable(){
      @Override
      public void dispose() {
        splitter.dispose();
      }
    });

    if (tableModel.getRowCount() != 0) {
      myTable.getSelectionModel().addSelectionInterval(0,0);
    }
    return splitter;
  }

  class MyTableModel extends AbstractTableModel {
    @Override
    public int getRowCount() {
      return myChecked.length;
    }

    @Override
    public String getColumnName(int column) {
      return column == CHECK_COLUMN ? " " : JavaRefactoringBundle.message("method.column");
    }

    @Override
    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_COLUMN) {
        return Boolean.class;
      }
      return String.class;
    }


    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if(columnIndex == CHECK_COLUMN) {
        return Boolean.valueOf(myChecked[rowIndex]);
      }
      else {
        return myMethodText[rowIndex];
      }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if(columnIndex == CHECK_COLUMN) {
        myChecked[rowIndex] = ((Boolean) aValue).booleanValue();
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == CHECK_COLUMN;
    }

    void updateData() {
      fireTableDataChanged();
    }
  }
}