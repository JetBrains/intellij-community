package com.intellij.debugger.ui.tree.render.configurables;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.CompletionEditor;
import com.intellij.debugger.ui.tree.render.EnumerationChildrenRenderer;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class NamedChildrenConfigurable implements UnnamedConfigurable{
  private Table myTable;
  private final EnumerationChildrenRenderer myRenderer;
  private JPanel myPanel;
  private JLabel myTableLabel;
  private JButton myButtonAdd;
  private JButton myButtonRemove;
  private JButton myButtonUp;
  private JButton myButtonDown;
  private CompletionEditor myCompletionEditor;

  public NamedChildrenConfigurable(Project project, EnumerationChildrenRenderer renderer) {
    myRenderer = renderer;

    myTableLabel.setLabelFor(myTable);

    getModel().addColumn(DebuggerBundle.message("label.named.children.configurable.table.header.column.name"), (Object[])null);
    final String expressionColumnName = DebuggerBundle.message("label.named.children.configurable.table.header.column.expression");
    getModel().addColumn(expressionColumnName, (Object[])null);

    PsiClass psiClass = DebuggerUtils.findClass(myRenderer.getClassName(), project, GlobalSearchScope.allScope(project));
    myCompletionEditor = ((DebuggerUtilsEx)DebuggerUtils.getInstance()).createEditor(project, psiClass, "NamedChildrenConfigurable");

    myTable.setDragEnabled(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));

    myTable.getColumn(expressionColumnName).setCellEditor(new AbstractTableCellEditor() {
      public Object getCellEditorValue() {
        return myCompletionEditor.getText();
      }

      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        myCompletionEditor.setText((TextWithImports)value);
        return myCompletionEditor;
      }
    });

    myButtonAdd.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        getModel().addRow(new Object[] {"", DebuggerUtils.getInstance().createExpressionWithImports("") });
      }
    });

    myButtonRemove.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selectedRow = myTable.getSelectedRow();
        if(selectedRow >= 0 && selectedRow < myTable.getRowCount()) {
          getModel().removeRow(selectedRow);
        }
      }
    });

    myButtonDown.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableUtil.moveSelectedItemsDown(myTable);
      }
    });

    myButtonUp.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableUtil.moveSelectedItemsUp(myTable);
      }
    });

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });
    updateButtons();
  }

  private void updateButtons() {
    int selectedRow = myTable.getSelectedRow();
    myButtonRemove.setEnabled(selectedRow != -1);
    myButtonUp.setEnabled(selectedRow > 0);
    myButtonDown.setEnabled(selectedRow < myTable.getRowCount() - 1);
  }

  private DefaultTableModel getModel() {
    return ((DefaultTableModel)myTable.getModel());
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
    DefaultTableModel model = getModel();

    final int size = model.getRowCount();
    java.util.List<Pair<String, TextWithImports>> result = new ArrayList<Pair<String, TextWithImports>>();

    for (int idx = 0; idx < size; idx++) {
      result.add(new Pair<String, TextWithImports>((String)model.getValueAt(idx, 0), (TextWithImports)model.getValueAt(idx, 1)));
    }
    myRenderer.setChildren(result);
  }

  public void reset() {
    while(myTable.getModel().getRowCount() > 0) {
      getModel().removeRow(0);
    }

    for (Pair<String, TextWithImports> pair : myRenderer.getChildren()) {
      getModel().addRow(new Object[]{pair.getFirst(), pair.getSecond()});
    }
  }

  public void disposeUIResources() {
    if (myCompletionEditor != null) {
      myCompletionEditor.dispose();
      myCompletionEditor = null;
    }
  }


  /*
  private class TextWithImportsTableRenderer implements TableCellRenderer{
    private final CompletionEditor myEditor;

    private TextWithImportsTableRenderer () {
      PsiClass psiClass = DebuggerUtils.findClass(myRenderer.getClassName(), myProject);
      myEditor = DebuggerUtils.getInstance().createEditor(myProject, psiClass, "NamedChildrenConfigurable");
    }


    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if(hasFocus) {
        myEditor.setText((TextWithImports)value);
        return myEditor;
      }
      else {
        TableCellRenderer defaultRenderer = myTable.getDefaultRenderer(String.class);
        return defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      }
    }
  }
  */

}
