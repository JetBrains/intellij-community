// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.ValidatingComponent;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author dsl
 */
public class AutomaticUsageRenamingDialog<T> extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(AutomaticUsageRenamingDialog.class);
  private static final int CHECK_COLUMN = 0;
  private static final int OLD_NAME_COLUMN = 1;
  private static final int NEW_NAME_COLUMN = 2;
  private final AutomaticUsageRenamer<T> myRenamer;
  private boolean[] myShouldRename;
  private String[] myNewNames;
  private MyTableModel myTableModel;
  private JBTable myTable;
  private DocumentAdapter myCellEditorListener;
  private ValidatingComponent<?> myValidatingComponent;

  public AutomaticUsageRenamingDialog(Project project, AutomaticUsageRenamer<T> renamer) {
    super(project, true);
    myRenamer = renamer;
    populateData();
    setTitle(myRenamer.getDialogTitle());
    setOKButtonText(IdeBundle.message("button.ok"));
    init();
  }

  private void populateData() {
    myNewNames = new String[getElementCount()];
    myShouldRename = new boolean[getElementCount()];
    for (int i = 0; i < getElementCount(); i++) {
      myNewNames[i] = myRenamer.getNewElementName(getElements().get(i));
      myShouldRename[i] = myRenamer.isCheckedInitially(getElements().get(i));
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.rename.AutomaticRenamingDialog";
  }

  private int getElementCount() {
    return getElements().size();
  }

  @Override
  protected JComponent createNorthPanel() {
    final Box box = Box.createHorizontalBox();
    box.add(new JLabel(myRenamer.getDialogDescription()));
    box.add(Box.createHorizontalGlue());
    return box;
  }

  @Override
  public void show() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    super.show();
  }

  private void handleChanges() {
    updateRenamer();

    boolean okActionEnabled = true;
    setOKActionEnabled(okActionEnabled);

    refreshValidatingComponent();
  }

  private void refreshValidatingComponent() {
    int selectedRow = myTable.getSelectedRow();
    if (selectedRow >= 0) {
      myValidatingComponent.setErrorText(null);
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  @Override
  protected JComponent createCenterPanel() {
    final Box box = Box.createVerticalBox();
    setupTable();

    myTableModel.addTableModelListener(e -> handleChanges());

    TableUtil.setupCheckboxColumn(myTable, CHECK_COLUMN);
    setupOldNameColumn();
    setupNewNameColumn();

    myValidatingComponent = new ValidatingComponent<JComponent>() {
      @Override
      protected JComponent createMainComponent() {
        return ScrollPaneFactory.createScrollPane(myTable);
      }
    };

    myValidatingComponent.doInitialize();

    box.add(myValidatingComponent);
    final Box buttonBox = Box.createHorizontalBox();
    buttonBox.add(Box.createHorizontalGlue());
    final JButton selectAllButton = new JButton(RefactoringBundle.message("select.all.button"));
    buttonBox.add(selectAllButton);
    buttonBox.add(Box.createHorizontalStrut(4));
    final JButton deselectAllButton = new JButton(RefactoringBundle.message("unselect.all.button"));
    buttonBox.add(deselectAllButton);
    selectAllButton.addActionListener(e -> {
      for (int i = 0; i < getElementCount(); i++) {
        myShouldRename[i] = true;
      }
      myTableModel.fireTableDataChanged();
    });

    deselectAllButton.addActionListener(e -> {
      for (int i = 0; i < getElementCount(); i++) {
        myShouldRename[i] = false;
      }
      myTableModel.fireTableDataChanged();
    });
    box.add(Box.createVerticalStrut(4));
    box.add(buttonBox);
    box.add(Box.createVerticalStrut(4));

    return box;
  }

  private void setupTable() {
    myTable = new JBTable();
    myTableModel = new MyTableModel();
    myTable.setModel(myTableModel);

    myTable.getSelectionModel().addListSelectionListener(e -> refreshValidatingComponent());
    myTable.setCellSelectionEnabled(false);
    myTable.setColumnSelectionAllowed(false);
    myTable.setRowSelectionAllowed(false);
    myTable.getTableHeader().setReorderingAllowed(false);
  }

  private void setupNewNameColumn() {
    myTable.getColumnModel().getColumn(NEW_NAME_COLUMN).setCellRenderer(new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        setToolTipText(null);
        append(String.valueOf(value), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });

    final JTextField textField = new JTextField("");
    textField.setBorder(JBUI.Borders.empty());
    myTable.getColumnModel().getColumn(NEW_NAME_COLUMN).setCellEditor(new DefaultCellEditor(textField) {
      @Override
      public boolean stopCellEditing() {
        removeListener(textField);
        return super.stopCellEditing();
      }

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, final int row, final int column) {
        super.getTableCellEditorComponent(table, value, isSelected, row, column);
        removeListener(textField);

        myCellEditorListener = new DocumentAdapter() {
          @Override
          protected void textChanged(@NotNull DocumentEvent e) {
            myTableModel.setValueAt(getCellEditorValue(), row, column);
            setChecked(row, true);
            getElements();
            textField.setToolTipText(null);
            Font font = textField.getFont();
            textField.setForeground(SimpleTextAttributes.REGULAR_ATTRIBUTES.getFgColor());
            textField.setFont(font.deriveFont(font.getStyle() & ~Font.ITALIC));
            repaintTable();
          }
        };
        textField.getDocument().addDocumentListener(myCellEditorListener);
        return textField;
      }

    });
  }

  private void repaintTable() {
    myTable.invalidate();
    myTable.repaint();
  }

  private void setupOldNameColumn() {
    myTable.getColumnModel().getColumn(OLD_NAME_COLUMN).setCellRenderer(new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        //noinspection unchecked
        T element = (T) value;
        setToolTipText(null);
        append(myRenamer.getName(element), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });
  }

  private void removeListener(JTextField textField) {
    if (myCellEditorListener != null) {
      textField.getDocument().removeDocumentListener(myCellEditorListener);
    }
  }

  @Override
  protected void doOKAction() {
    TableUtil.stopEditing(myTable);
    handleChanges();
    super.doOKAction();
  }

  private void updateRenamer() {
    for (int i = 0; i < getElementCount(); i++) {
      T element = getElements().get(i);
      if (myShouldRename[i]) {
        myRenamer.setRename(element, myNewNames[i]);
      }
      else {
        myRenamer.doNotRename(element);
      }
    }
  }

  protected void setChecked(int rowIndex, boolean checked) {
    myTableModel.setValueAt(checked, rowIndex, CHECK_COLUMN);
  }

  protected void setChecked(T element, boolean checked) {
    setChecked(getElements().indexOf(element), checked);
  }

  private List<? extends T> getElements() {
    return myRenamer.getElements();
  }

  protected boolean isChecked(T element) {
    return myShouldRename[getElements().indexOf(element)];
  }

  private class MyTableModel extends AbstractTableModel {
    MyTableModel() {
      InputMap inputMap = myTable.getInputMap();
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
      myTable.getActionMap().put("enable_disable", new MySpaceAction());
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
      myTable.getActionMap().put("enter", new EnterAction());
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public int getRowCount() {
      return getElementCount();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return switch (columnIndex) {
        case CHECK_COLUMN -> myShouldRename[rowIndex];
        case OLD_NAME_COLUMN -> getElements().get(rowIndex);
        case NEW_NAME_COLUMN -> myNewNames[rowIndex];
        default -> {
          LOG.assertTrue(false);
          yield null;
        }
      };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECK_COLUMN -> myShouldRename[rowIndex] = (Boolean)aValue;
        case NEW_NAME_COLUMN -> myNewNames[rowIndex] = (String)aValue;
        default -> LOG.assertTrue(false);
      }
      handleChanges();
      repaintTable();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex != OLD_NAME_COLUMN;
    }

    @Override
    @Nullable
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
        case OLD_NAME_COLUMN -> RefactoringBundle.message("automatic.renamer.entity.name.column", myRenamer.getEntityName());
        case NEW_NAME_COLUMN -> RefactoringBundle.message("automatic.renamer.rename.to.column");
        default -> " ";
      };
    }

    private class MySpaceAction extends AbstractAction {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;

        int row = myTable.getSelectionModel().getAnchorSelectionIndex();
        if (row >= 0 && row < getElementCount()) {
          myShouldRename[row] = !myShouldRename[row];
          fireTableDataChanged();
          repaintTable();
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTable, true));
        }
      }
    }

    private class EnterAction extends AbstractAction {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!myTable.isEditing()) {
          doOKAction();
        }
      }
    }
  }
}
