/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.ui.EnableDisableAction;
import com.intellij.refactoring.ui.StringTableCellEditor;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsagePreviewPanel;
import org.jetbrains.annotations.Nullable;

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
  private JBTable myTable;
  private JPanel myPanelForPreview;
  private JButton mySelectAllButton;
  private JButton myUnselectAllButton;
  private JPanel myPanel;
  private JSplitPane mySplitPane;
  private final Project myProject;
  private final UsagePreviewPanel myUsagePreviewPanel;
  private final JLabel myUsageFileLabel;
  private ListSelectionListener myListSelectionListener;

  public AutomaticRenamingDialog(Project project, AutomaticRenamer renamer) {
    super(project, true);
    myProject = project;
    myRenamer = renamer;
    myUsagePreviewPanel = new UsagePreviewPanel(myProject, new UsageViewPresentation());
    myUsageFileLabel = new JLabel();
    populateData();
    myTableModel = new MyTableModel(renamer.allowChangeSuggestedName());
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
      @Override
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

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(myRenamer.getDialogDescription()), BorderLayout.CENTER);
    final DefaultActionGroup actionGroup = new DefaultActionGroup(null, false);
    actionGroup.addAction(createRenameSelectedAction()).setAsSecondary(true);
    panel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true).getComponent(), BorderLayout.EAST);
    final Box box = Box.createHorizontalBox();
    box.add(panel);
    box.add(Box.createHorizontalGlue());
    return box;
  }

  @Override
  public void show() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    super.show();
  }

  protected void handleChanges() {
    final int selectedRow = myTable.getSelectedRow();
    if (selectedRow > -1) {
      final boolean validName = RenameUtil.isValidName(myProject, myRenames[selectedRow], myNewNames[selectedRow]);
      getOKAction().setEnabled(validName);
      setErrorText(validName ? null : "Identifier \'" + myNewNames[selectedRow] + "\' is invalid");
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    myTable.setModel(myTableModel);
    myTableModel.getSpaceAction().register();
    myTableModel.addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        handleChanges();
      }
    });
    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final int[] selectionRows = myTable.getSelectedRows();
        if (selectionRows != null) {
          compoundPopup().show(comp, x, y);
        }
      }
    });

    final TableColumnModel columnModel = myTable.getColumnModel();
    columnModel.getColumn(CHECK_COLUMN).setCellRenderer(new BooleanTableCellRenderer());
    TableUtil.setupCheckboxColumn(columnModel.getColumn(CHECK_COLUMN));

    columnModel.getColumn(NEW_NAME_COLUMN).setCellEditor(new StringTableCellEditor(myProject));
    mySelectAllButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < myShouldRename.length; i++) {
          myShouldRename[i] = true;
        }
        fireDataChanged();
      }
    });

    myUnselectAllButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < myShouldRename.length; i++) {
          myShouldRename[i] = false;
        }
        fireDataChanged();
      }
    });
    myListSelectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        myUsageFileLabel.setText("");
        int index = myTable.getSelectionModel().getLeadSelectionIndex();
        if (index != -1) {
          PsiNamedElement element = myRenames[index];
          UsageInfo usageInfo = new UsageInfo(element);
          myUsagePreviewPanel.updateLayout(Collections.singletonList(usageInfo));
          final PsiFile containingFile = element.getContainingFile();
          if (containingFile != null) {
            final VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
              myUsageFileLabel.setText(virtualFile.getName());
            }
          }
        }
        else {
          myUsagePreviewPanel.updateLayout(null);
        }
      }
    };
    myTable.getSelectionModel().addListSelectionListener(myListSelectionListener);

    myPanelForPreview.add(myUsagePreviewPanel, BorderLayout.CENTER);
    myUsagePreviewPanel.updateLayout(null);
    myPanelForPreview.add(myUsageFileLabel, BorderLayout.NORTH);
    mySplitPane.setDividerLocation(0.5);

    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);

    if (myTableModel.getRowCount() != 0) {
      myTable.getSelectionModel().addSelectionInterval(0,0);
    }
    return myPanel;
  }

  private JPopupMenu compoundPopup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(createRenameSelectedAction());
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
    return menu.getComponent();
  }

  private RenameSelectedAction createRenameSelectedAction() {
    return new RenameSelectedAction(myTable, myTableModel) {
      @Override
      protected boolean isValidName(String inputString, int selectedRow) {
        return RenameUtil.isValidName(myProject, myRenames[selectedRow], inputString);
      }
    };
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

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  @Override
  protected void doOKAction() {
    TableUtil.stopEditing(myTable);
    updateRenamer();
    super.doOKAction();
  }

  @Override
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

  private void createUIComponents() {
    myTable = new JBTable();
    myTable.setRowHeight(myTable.getFontMetrics(UIManager.getFont("Table.font").deriveFont(Font.BOLD)).getHeight() + 4);
  }

  private class MyTableModel extends AbstractTableModel {
    private final boolean myAllowRename;

    private MyTableModel(boolean allowRename) {
      myAllowRename = allowRename;
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public int getRowCount() {
      return myShouldRename.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      switch(columnIndex) {
        case CHECK_COLUMN:
          return Boolean.valueOf(myShouldRename[rowIndex]);
        case OLD_NAME_COLUMN:
          return "<html><nobr>" + RefactoringUIUtil.getDescription(myRenames[rowIndex], true) + "</nobr></html>";
        case NEW_NAME_COLUMN:
          return myNewNames[rowIndex];
        default:
          LOG.assertTrue(false);
          return null;
      }
    }

    @Override
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

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex != OLD_NAME_COLUMN && (myAllowRename || columnIndex != NEW_NAME_COLUMN);
    }

    @Override
    public Class getColumnClass(int columnIndex) {
      switch(columnIndex) {
        case CHECK_COLUMN: return Boolean.class;
        case OLD_NAME_COLUMN: return String.class;
        case NEW_NAME_COLUMN: return String.class;
        default: return null;
      }
    }

    @Override
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
      @Override
      protected JTable getTable() {
        return myTable;
      }

      @Override
      protected boolean isRowChecked(int row) {
        return myShouldRename[row];
      }

      @Override
      protected void applyValue(int[] rows, boolean valueToBeSet) {
        for (final int row : rows) {
          myShouldRename[row] = valueToBeSet;
        }
        fireDataChanged();
      }
    }
  }

  public abstract static class RenameSelectedAction extends AnAction {
  
    private JTable myTable;
    private AbstractTableModel myModel;

    public RenameSelectedAction(JTable table, final AbstractTableModel model) {
      super("Rename Selected");
      myTable = table;
      myModel = model;
    }
  
    @Override
    public void actionPerformed(AnActionEvent e) {
      final int[] selectedRows = myTable.getSelectedRows();
  
      final String newName = Messages.showInputDialog(myTable, "New name", "Rename Selected", null,
                                                      (String)myModel.getValueAt(selectedRows[0], NEW_NAME_COLUMN),
                                                      new InputValidatorEx() {
                                                        @Override
                                                        public boolean checkInput(String inputString) {
                                                          return getErrorText(inputString) == null;
                                                        }

                                                        @Override
                                                        public boolean canClose(String inputString) {
                                                          return checkInput(inputString);
                                                        }

                                                        @Nullable
                                                        @Override
                                                        public String getErrorText(String inputString) {
                                                          final int selectedRow = myTable.getSelectedRow();
                                                          if (!isValidName(inputString, selectedRow)) {
                                                            return "Identifier \'" + inputString + "\' is invalid";
                                                          }
                                                          return null;
                                                        }
                                                      });
      if (newName == null) return;
  
      for (int i : selectedRows) {
        myModel.setValueAt(newName, i, NEW_NAME_COLUMN);
      }
      myModel.fireTableDataChanged();
      for (int row : selectedRows) {
        myTable.getSelectionModel().addSelectionInterval(row, row);
      }
    }
  
    protected abstract boolean isValidName(String inputString, int selectedRow);
  
    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myTable.getSelectedRows().length > 0);
    }
  }
}
