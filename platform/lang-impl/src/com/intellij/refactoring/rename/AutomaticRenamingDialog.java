// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SyntheticElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.ui.EnableDisableAction;
import com.intellij.refactoring.ui.StringTableCellEditor;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TableUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsagePreviewPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.List;
import java.util.*;

public class AutomaticRenamingDialog extends DialogWrapper {
  private static final int CHECK_COLUMN = 0;
  private static final int OLD_NAME_COLUMN = 1;
  private static final int NEW_NAME_COLUMN = 2;

  private final Project myProject;
  private final AutomaticRenamer myRenamer;
  private final boolean[] myShouldRename;
  private final String[] myNewNames;
  private final PsiNamedElement[] myRenames;
  private final MyTableModel myTableModel;

  private JPanel myPanel;
  private JSplitPane mySplitPane;
  private JBTable myTable;
  private JPanel myOptionsPanel;
  private JBCheckBox mySearchInComments;
  private JBCheckBox mySearchTextOccurrences;
  private JButton mySelectAllButton;
  private JButton myUnselectAllButton;
  private JPanel myPanelForPreview;
  private UsagePreviewPanel myUsagePreviewPanel;
  private JLabel myUsageFileLabel;
  private ListSelectionListener myListSelectionListener;

  public AutomaticRenamingDialog(@NotNull Project project, @NotNull AutomaticRenamer renamer) {
    super(project, true);
    myProject = project;
    myRenamer = renamer;

    Map<PsiNamedElement, String> renames = renamer.getRenames();

    List<PsiNamedElement> temp = new ArrayList<>();
    for (PsiNamedElement namedElement : renames.keySet()) {
      String newName = renames.get(namedElement);
      if (newName != null) temp.add(namedElement);
    }
    myRenames = temp.toArray(PsiNamedElement.EMPTY_ARRAY);
    Arrays.sort(myRenames, (e1, e2) -> Comparing.compare(e1.getName(), e2.getName()));

    myNewNames = new String[myRenames.length];
    for (int i = 0; i < myNewNames.length; i++) {
      myNewNames[i] = renames.get(myRenames[i]);
    }

    myShouldRename = new boolean[myRenames.length];
    if (renamer.isSelectedByDefault()) {
      Arrays.fill(myShouldRename, true);
    }

    myTableModel = new MyTableModel(renamer.allowChangeSuggestedName());

    setTitle(renamer.getDialogTitle());
    init();
  }

  private void createUIComponents() {
    myTable = new JBTable();
    myTable.setRowHeight(myTable.getFontMetrics(UIManager.getFont("Table.font").deriveFont(Font.BOLD)).getHeight() + 4);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.rename.AutomaticRenamingDialog";
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(myRenamer.getDialogDescription()), BorderLayout.CENTER);
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.addAction(createRenameSelectedAction()).setAsSecondary(true);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("AutoRenaming", actionGroup, true);
    toolbar.setTargetComponent(myTable);
    panel.add(toolbar.getComponent(), BorderLayout.EAST);
    final Box box = Box.createHorizontalBox();
    box.add(panel);
    box.add(Box.createHorizontalGlue());
    return box;
  }

  @Override
  public void show() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      super.show();
    }
  }

  private void handleChanges() {
    for (int i = 0; i < myNewNames.length; i++) {
      String newName = myNewNames[i];
      if (myShouldRename[i] && !RenameUtil.isValidName(myProject, myRenames[i], newName)) {
        getOKAction().setEnabled(false);
        setErrorText(RefactoringBundle.message("automatic.renaming.dialog.identifier.invalid.error", newName));
        return;
      }
    }
    getOKAction().setEnabled(true);
    setErrorText(null);
  }

  @Override
  protected JComponent createCenterPanel() {
    myUsagePreviewPanel = new UsagePreviewPanel(myProject, new UsageViewPresentation());
    myUsageFileLabel = new JLabel();

    myTable.setModel(myTableModel);
    myTableModel.getSpaceAction().register();
    myTableModel.addTableModelListener(e -> handleChanges());
    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        if (myTable.getSelectedRows() != null) {
          compoundPopup().show(comp, x, y);
        }
      }
    });

    TableColumnModel columnModel = myTable.getColumnModel();
    columnModel.getColumn(CHECK_COLUMN).setCellRenderer(new BooleanTableCellRenderer());
    TableUtil.setupCheckboxColumn(columnModel.getColumn(CHECK_COLUMN), 0);
    columnModel.getColumn(NEW_NAME_COLUMN).setCellEditor(new StringTableCellEditor(myProject));

    mySelectAllButton.addActionListener(e -> {
      Arrays.fill(myShouldRename, true);
      fireDataChanged();
    });

    myUnselectAllButton.addActionListener(e -> {
      Arrays.fill(myShouldRename, false);
      fireDataChanged();
    });

    myListSelectionListener = e -> {
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
    };
    myTable.getSelectionModel().addListSelectionListener(myListSelectionListener);

    myPanelForPreview.add(myUsagePreviewPanel, BorderLayout.CENTER);
    myUsagePreviewPanel.updateLayout(null);
    myPanelForPreview.add(myUsageFileLabel, BorderLayout.NORTH);
    double top = mySplitPane.getTopComponent().getPreferredSize().getHeight();
    double bottom = mySplitPane.getBottomComponent().getPreferredSize().getHeight();
    mySplitPane.setDividerLocation(top / (top + bottom));

    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      SwingUtilities.invokeLater(() -> {
        if (myTableModel.getRowCount() != 0) {
          myTable.getSelectionModel().addSelectionInterval(0, 0);
        }
      });
    }
    myOptionsPanel.setVisible(false);

    return myPanel;
  }

  private JPopupMenu compoundPopup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(createRenameSelectedAction());
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu("AutomaticRenamingDialog", group);
    return menu.getComponent();
  }

  private RenameSelectedAction createRenameSelectedAction() {
    return new RenameSelectedAction(myTable, myTableModel) {
      @Override
      protected boolean isValidName(String inputString, int selectedRow) {
        return RenameUtil.isValidName(myProject, myRenames[selectedRow], inputString);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
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

  public void showOptionsPanel() {
    myOptionsPanel.setVisible(true);
  }

  public boolean isSearchInComments() {
    return mySearchInComments.isSelected();
  }

  public boolean isSearchTextOccurrences() {
    return mySearchTextOccurrences.isSelected();
  }

  private final class MyTableModel extends AbstractTableModel {
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
      return switch (columnIndex) {
        case CHECK_COLUMN -> myShouldRename[rowIndex];
        case OLD_NAME_COLUMN -> "<html><nobr>" + RefactoringUIUtil.getDescription(myRenames[rowIndex], true) + "</nobr></html>";
        case NEW_NAME_COLUMN -> myNewNames[rowIndex];
        default -> null;
      };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECK_COLUMN -> myShouldRename[rowIndex] = ((Boolean)aValue).booleanValue();
        case NEW_NAME_COLUMN -> myNewNames[rowIndex] = (String)aValue;
      }
      handleChanges();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex != OLD_NAME_COLUMN && (myAllowRename || columnIndex != NEW_NAME_COLUMN)
        && !(myRenames[rowIndex] instanceof SyntheticElement);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return switch (columnIndex) {
        case CHECK_COLUMN -> Boolean.class;
        case OLD_NAME_COLUMN, NEW_NAME_COLUMN -> String.class;
        default -> null;
      };
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case OLD_NAME_COLUMN -> RefactoringBundle.message("automatic.renamer.entity.name.column", myRenamer.entityName());
        case NEW_NAME_COLUMN -> RefactoringBundle.message("automatic.renamer.rename.to.column");
        default -> " ";
      };
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
    private final JTable myTable;
    private final AbstractTableModel myModel;

    public RenameSelectedAction(JTable table, final AbstractTableModel model) {
      super(RefactoringBundle.message("automatic.renaming.dialog.rename.selected.title"));
      myTable = table;
      myModel = model;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      int[] selectedRows = myTable.getSelectedRows();
      String initial = (String)myModel.getValueAt(selectedRows[0], NEW_NAME_COLUMN);
      String newName = Messages.showInputDialog(myTable, RefactoringBundle.message("automatic.renaming.dialog.new.name.label"),
                                                RefactoringBundle.message("automatic.renaming.dialog.rename.selected.title"), null, initial, new InputValidatorEx() {
        @Override
        public boolean canClose(String inputString) {
          return checkInput(inputString);
        }

        @Nullable
        @Override
        public String getErrorText(@NlsSafe String inputString) {
          final int selectedRow = myTable.getSelectedRow();
          if (!isValidName(inputString, selectedRow)) {
            return RefactoringBundle.message("text.identifier.invalid", inputString);
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
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myTable.getSelectedRows().length > 0);
    }
  }
}