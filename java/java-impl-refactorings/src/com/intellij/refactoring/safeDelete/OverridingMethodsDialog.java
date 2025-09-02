// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteOverridingMethodUsageInfo;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OverridingMethodsDialog extends DialogWrapper {
  private final Project myProject;
  private final List<? extends UsageInfo> myOverridingMethods;
  private final String[] myMethodText;
  private final boolean[] myChecked;

  private static final int CHECK_COLUMN = 0;
  private JBTable myTable;
  private final UsagePreviewPanel myUsagePreviewPanel;

  public OverridingMethodsDialog(Project project, List<? extends UsageInfo> overridingMethods) {
    super(project, true);
    myProject = project;
    myOverridingMethods = overridingMethods;
    myChecked = new boolean[myOverridingMethods.size()];
    Arrays.fill(myChecked, true);

    myMethodText = new String[myOverridingMethods.size()];
    Arrays.setAll(myMethodText, i -> getElementDescription(myOverridingMethods.get(i)));
    myUsagePreviewPanel = new UsagePreviewPanel(project, new UsageViewPresentation());
    setTitle(getTitleText());
    init();
  }

  protected @NlsContexts.DialogTitle @NotNull String getTitleText() {
    return JavaRefactoringBundle.message("unused.overriding.methods.title");
  }

  protected @NlsContexts.DialogMessage @NotNull String getDescriptionText() {
    return JavaRefactoringBundle.message("there.are.unused.methods.that.override.methods.you.delete");
  }

  protected @NlsContexts.ColumnName @NotNull String getColumnName() {
    return JavaRefactoringBundle.message("method.column");
  }

  protected String getElementDescription(UsageInfo info) {
    PsiElement overridingMethod = ((SafeDeleteOverridingMethodUsageInfo)info).getOverridingMethod();
    if (overridingMethod instanceof PsiMethod method) {
      int options = PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                    PsiFormatUtilBase.SHOW_NAME |
                    PsiFormatUtilBase.SHOW_PARAMETERS |
                    PsiFormatUtilBase.SHOW_TYPE;
      return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, PsiFormatUtilBase.SHOW_TYPE);
    }
    else {
      return SymbolPresentationUtil.getSymbolPresentableText(overridingMethod);
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.safeDelete.OverridingMethodsDialog";
  }

  @Override
  public @Nullable Dimension getInitialSize() {
    return JBUI.DialogSizes.large();
  }

  public ArrayList<UsageInfo> getSelected() {
    ArrayList<UsageInfo> result = new ArrayList<>();
    for (int i = 0; i < myChecked.length; i++) {
      if (myChecked[i]) {
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
    JPanel panel = new JPanel(new BorderLayout(0, UIUtil.DEFAULT_VGAP));
    panel.add(new JLabel(getDescriptionText()), BorderLayout.NORTH);
    final MyTableModel tableModel = new MyTableModel();
    myTable = new JBTable(tableModel);
    myTable.setShowGrid(false);

    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn checkboxColumn = columnModel.getColumn(CHECK_COLUMN);
    TableUtil.setupCheckboxColumn(checkboxColumn, columnModel.getColumnMargin());
    checkboxColumn.setCellRenderer(new BooleanTableCellRenderer());

    // make SPACE check/uncheck selected rows
    @NonNls InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    final @NonNls ActionMap actionMap = myTable.getActionMap();
    actionMap.put("enable_disable", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        int[] rows = myTable.getSelectedRows();
        if (rows.length > 0) {
          for (int row : rows) {
            myChecked[row] = !myChecked[row];
          }
          myTable.repaint();
        }
      }
    });
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
        int row = myTable.getSelectedRow();
        if (row < 0 || row >= myOverridingMethods.size()) return false;
        myChecked[row] = !myChecked[row];
        myTable.repaint();
        return true;
      }
    }.installOn(myTable);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int index = myTable.getSelectionModel().getLeadSelectionIndex();
        if (index != -1) {
          UsageInfo usageInfo = myOverridingMethods.get(index);
          myUsagePreviewPanel.updateLayout(myProject, Collections.singletonList(usageInfo));
        }
        else {
          myUsagePreviewPanel.updateLayout(myProject, null);
        }
      }
    };
    myTable.getSelectionModel().addListSelectionListener(selectionListener);

    final Splitter splitter = new Splitter(true, 0.4f);
    splitter.setFirstComponent(scrollPane);
    splitter.setSecondComponent(myUsagePreviewPanel);
    Disposer.register(myDisposable, new Disposable(){
      @Override
      public void dispose() {
        splitter.dispose();
      }
    });
    panel.add(splitter, BorderLayout.CENTER);

    if (tableModel.getRowCount() != 0) {
      SwingUtilities.invokeLater(() -> myTable.setRowSelectionInterval(0, 0));
    }
    return panel;
  }

  class MyTableModel extends AbstractTableModel {
    @Override
    public int getRowCount() {
      return myChecked.length;
    }

    @Override
    public String getColumnName(int column) {
      return column == CHECK_COLUMN ? " " : OverridingMethodsDialog.this.getColumnName();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == CHECK_COLUMN ? Boolean.class : String.class;
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return columnIndex == CHECK_COLUMN ? Boolean.valueOf(myChecked[rowIndex]) : myMethodText[rowIndex];
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_COLUMN) {
        myChecked[rowIndex] = ((Boolean) aValue).booleanValue();
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == CHECK_COLUMN;
    }
  }
}