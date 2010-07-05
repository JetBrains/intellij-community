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

package com.intellij.refactoring.rename;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.ui.EnableDisableAction;
import com.intellij.refactoring.ui.StringTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.TableUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author dsl
 */
public class AutomaticRenamingDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.AutomaticRenamingDialog");
  private static final int CHECK_COLUMN = 0;
  private static final int OLD_NAME_COLUMN = 1;
  private static final int NEW_NAME_COLUMN = 2;
  private final AutomaticRenamer myRenamer;
  private boolean[] myShouldRename;
  private String[] myNewNames;
  private PsiNamedElement[] myRenames;
  private MyTableModel myTableModel;
  private Table myTable;
  private JPanel myPanelForPreview;
  private JButton mySelectAllButton;
  private JButton myUnselectAllButton;
  private JPanel myPanel;
  private JSplitPane mySplitPane;
  private final Project myProject;
  private final UsagePreviewPanel myUsagePreviewPanel;
  private ListSelectionListener myListSelectionListener;

  public AutomaticRenamingDialog(Project project, AutomaticRenamer renamer) {
    super(project, true);
    myProject = project;
    myRenamer = renamer;
    myUsagePreviewPanel = new UsagePreviewPanel(myProject);
    populateData();
    setTitle(myRenamer.getDialogTitle());
    init();
  }

  private void populateData() {
    final Map<PsiNamedElement, String> renames = myRenamer.getRenames();

    List<PsiNamedElement> temp = new ArrayList<PsiNamedElement>();
    for (final PsiNamedElement namedElement : renames.keySet()) {
      final String newName = renames.get(namedElement);
      if (newName != null) temp.add(namedElement);
    }

    myRenames = temp.toArray(new PsiNamedElement[temp.size()]);
    Arrays.sort(myRenames, new Comparator<PsiNamedElement>() {
      public int compare(final PsiNamedElement e1, final PsiNamedElement e2) {
        return Comparing.compare(e1.getName(), e2.getName());
      }
    });

    myNewNames = new String[myRenames.length];
    for (int i = 0; i < myNewNames.length; i++) {
      myNewNames[i] = renames.get(myRenames[i]);
    }

    myShouldRename = new boolean[myRenames.length];
    if (myRenamer.isSelectedByDefault()) {
      for(int i=0; i<myShouldRename.length; i++) {
        myShouldRename [i] = true;
      }
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.rename.AutomaticRenamingDialog";
  }

  protected JComponent createNorthPanel() {
    final Box box = Box.createHorizontalBox();
    box.add(new JLabel(myRenamer.getDialogDescription()));
    box.add(Box.createHorizontalGlue());
    return box;
  }

  public void show() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    super.show();
  }

  protected void handleChanges() {
    final int selectedRow = myTable.getSelectedRow();
    final boolean validName = RenameUtil.isValidName(myProject, myRenames[selectedRow], myNewNames[selectedRow]);
    getOKAction().setEnabled(validName);
    setErrorText(validName ? null : "Identifier \'" + myNewNames[selectedRow] + "\' is invalid");
  }

  protected JComponent createCenterPanel() {
    myTableModel = new MyTableModel();
    myTable.setModel(myTableModel);
    myTableModel.getSpaceAction().register();
    myTableModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        handleChanges();
      }
    });

    final TableColumnModel columnModel = myTable.getColumnModel();
    columnModel.getColumn(CHECK_COLUMN).setCellRenderer(new BooleanTableCellRenderer());
    final int checkBoxWidth = new JCheckBox().getPreferredSize().width;
    columnModel.getColumn(CHECK_COLUMN).setMaxWidth(checkBoxWidth);
    columnModel.getColumn(CHECK_COLUMN).setMinWidth(checkBoxWidth);

    columnModel.getColumn(NEW_NAME_COLUMN).setCellEditor(new StringTableCellEditor(myProject));
    mySelectAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < myShouldRename.length; i++) {
          myShouldRename[i] = true;
        }
        fireDataChanged();
      }
    });

    myUnselectAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < myShouldRename.length; i++) {
          myShouldRename[i] = false;
        }
        fireDataChanged();
      }
    });
    myListSelectionListener = new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        int index = myTable.getSelectionModel().getLeadSelectionIndex();
        if (index != -1) {
          PsiNamedElement element = myRenames[index];
          UsageInfo usageInfo = new UsageInfo(element);
          myUsagePreviewPanel.updateLayout(Collections.singletonList(usageInfo));
        }
        else {
          myUsagePreviewPanel.updateLayout(null);
        }
      }
    };
    myTable.getSelectionModel().addListSelectionListener(myListSelectionListener);

    myPanelForPreview.add(myUsagePreviewPanel, BorderLayout.CENTER);
    myUsagePreviewPanel.updateLayout(null);
    mySplitPane.setDividerLocation(0.5);
    
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);

    if (myTableModel.getRowCount() != 0) {
      myTable.getSelectionModel().addSelectionInterval(0,0);
    }
    return myPanel;
  }

  private void fireDataChanged() {
    int[] selectedRows = myTable.getSelectedRows();
    myTable.getSelectionModel().removeListSelectionListener(myListSelectionListener);

    myTableModel.fireTableDataChanged();
    for (int selectedRow : selectedRows) {
      myTable.addRowSelectionInterval(selectedRow, selectedRow);
    }
    myTable.getSelectionModel().addListSelectionListener(myListSelectionListener);
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  protected void doOKAction() {
    TableUtil.stopEditing(myTable);
    updateRenamer();
    super.doOKAction();
  }

  protected void dispose() {
    Disposer.dispose(myUsagePreviewPanel);
    super.dispose();
  }

  private void updateRenamer() {
    for (int i = 0; i < myRenames.length; i++) {
      PsiNamedElement element = myRenames[i];
      if (myShouldRename[i]) {
        myRenamer.setRename(element, myNewNames[i]);
      }
      else {
        myRenamer.doNotRename(element);
      }
    }
  }

  private class MyTableModel extends AbstractTableModel {
    public int getColumnCount() {
      return 3;
    }

    public int getRowCount() {
      return myShouldRename.length;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch(columnIndex) {
        case CHECK_COLUMN:
          return Boolean.valueOf(myShouldRename[rowIndex]);
        case OLD_NAME_COLUMN:
          final PsiNamedElement namedElement = myRenames[rowIndex];
          String location = null;
          if (namedElement instanceof NavigationItem) {
            final ItemPresentation presentation = ((NavigationItem)namedElement).getPresentation();
            if (presentation != null) {
              location = presentation.getLocationString();
            }
          }
          return namedElement.getName() + (location != null ? " " + location : "");
        case NEW_NAME_COLUMN:
          return myNewNames[rowIndex];
        default:
          LOG.assertTrue(false);
          return null;
      }
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch(columnIndex) {
        case CHECK_COLUMN:
          myShouldRename[rowIndex] = ((Boolean)aValue).booleanValue();
          break;
        case NEW_NAME_COLUMN:
          myNewNames[rowIndex] = (String) aValue;
          break;
        default:
          LOG.assertTrue(false);
      }
      handleChanges();
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex != OLD_NAME_COLUMN;
    }

    public Class getColumnClass(int columnIndex) {
      switch(columnIndex) {
        case CHECK_COLUMN: return Boolean.class;
        case OLD_NAME_COLUMN: return String.class;
        case NEW_NAME_COLUMN: return String.class;
        default: return null;
      }
    }

    public String getColumnName(int column) {
      switch(column) {
        case OLD_NAME_COLUMN:
          return RefactoringBundle.message("automatic.renamer.enity.name.column", myRenamer.entityName());
        case NEW_NAME_COLUMN:
          return RefactoringBundle.message("automatic.renamer.rename.to.column");
        default:
          return " ";
      }
    }

    private MyEnableDisable getSpaceAction() {
      return this.new MyEnableDisable();
    }

    private class MyEnableDisable extends EnableDisableAction {
      protected JTable getTable() {
        return myTable;
      }

      protected boolean isRowChecked(int row) {
        return myShouldRename[row];
      }

      protected void applyValue(int[] rows, boolean valueToBeSet) {
        for (final int row : rows) {
          myShouldRename[row] = valueToBeSet;
        }
        fireDataChanged();
      }
    }
  }

}
