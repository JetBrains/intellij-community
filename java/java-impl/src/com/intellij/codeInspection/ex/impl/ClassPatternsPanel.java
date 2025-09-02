// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex.impl;

import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

class ClassPatternsPanel extends JPanel {

  private final List<EntryPointsManagerBase.ClassPattern> myModifiedPatterns;
  private final JBTable myTable;

  ClassPatternsPanel(List<EntryPointsManagerBase.ClassPattern> patterns) {
    super(new BorderLayout());
    myModifiedPatterns = patterns;
    myTable = createTableForPatterns();
    final String addClassMessage = JavaBundle.message("class.patterns.panel.add.class");
    final ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myTable)
      .setAddAction(button -> {
        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myTable));
        if (project == null) project = ProjectManager.getInstance().getDefaultProject();
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
          .createWithInnerClassesScopeChooser(addClassMessage, GlobalSearchScope.allScope(project), ClassFilter.ALL, null);
        chooser.showDialog();
        final PsiClass selected = chooser.getSelected();
        if (selected != null) {
          insertRow(selected.getQualifiedName());
        }
      })
      .setAddActionName(addClassMessage)
      .setRemoveAction(button -> {
        TableUtil.removeSelectedItems(myTable);
        myTable.repaint();
      })
      .setRemoveActionUpdater(e -> myTable.getSelectedRow() >= 0);
    add(SeparatorFactory.createSeparator(JavaBundle.message("class.patterns.separator.mark.code.as.entry.point.if.qualified.name.matches"), null), BorderLayout.NORTH);
    add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
    add(new MultiLineLabel(JavaBundle.message("label.class.pattern.syntax.explanation")), BorderLayout.SOUTH);
    setPreferredSize(new JBDimension(-1, 250));
  }

  private void insertRow(String pattern) {
    EntryPointsManagerBase.ClassPattern classPattern = new EntryPointsManagerBase.ClassPattern();
    classPattern.pattern = pattern;
    myModifiedPatterns.add(classPattern);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    final int row = myModifiedPatterns.size() - 1;
    model.fireTableRowsInserted(row, row);
    myTable.setRowSelectionInterval(row, row);
  }

  private JBTable createTableForPatterns() {
    TableModel dataModel = new MyTableModel();

    final JBTable result = new JBTable(dataModel);
    result.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    result.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof String && ((String)value).isEmpty()) {
          setText(JavaBundle.message("table.cell.constructors"));
          setForeground(NamedColorUtil.getInactiveTextColor());
        }
        else if (value instanceof String) {
          setText((String)value);
          setForeground(UIUtil.getTableForeground(isSelected));
        }
        return component;
      }
    });

    TableCellEditor editor = result.getDefaultEditor(String.class);
    if (editor instanceof DefaultCellEditor) {
      ((DefaultCellEditor)editor).setClickCountToStart(1);
    }

    final TableColumn column = result.getTableHeader().getColumnModel().getColumn(0);
    column.setResizable(false);
    final int width = 15 + result.getTableHeader().getFontMetrics(result.getTableHeader().getFont()).stringWidth(result.getColumnName(0));
    column.setMaxWidth(width);
    column.setMinWidth(width);

    return result;
  }

  public @Nls String getValidationError(Project project) {
    TableUtil.stopEditing(myTable);
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
    final ClassPatternValidator validator = new ClassPatternValidator(nameHelper);
    for (EntryPointsManagerBase.ClassPattern pattern : myModifiedPatterns) {
      final String errorText = validator.getErrorText(pattern.pattern);
      if (errorText != null) {
        return errorText;
      }

      final String subst = pattern.method.replace("*", "");
      if (!subst.isEmpty()) {
        if (!nameHelper.isIdentifier(subst)) {
          return JavaBundle.message("class.patterns.error.method.pattern.0.must.be.a.valid.java.identifier", pattern.method);
        }
      }
    }
    return null;
  }

  private static class ClassPatternValidator implements InputValidatorEx {
    private final PsiNameHelper myNameHelper;

    ClassPatternValidator(PsiNameHelper nameHelper) {
      myNameHelper = nameHelper;
    }

    @Override
    public @Nullable String getErrorText(String inputString) {
      String errorMessage = JavaBundle.message("class.patterns.error.class.pattern.0.must.be.a.valid.java.qualifier");
      if (inputString.startsWith(".")) return errorMessage;
      final String qName = inputString.replace("*", "").replace(".", "");
      return !StringUtil.isEmpty(qName) && !myNameHelper.isQualifiedName(qName) ? errorMessage : null;
    }

    @Override
    public boolean canClose(String inputString) {
      return getErrorText(inputString) == null;
    }
  }

  private class MyTableModel extends AbstractTableModel implements ItemRemovable {
    private final @NlsContexts.ColumnName String[] myNames;

    MyTableModel() {
      myNames = new String[] {
        JavaBundle.message("column.name.with.subclasses.entry.point"),
        JavaBundle.message("column.name.class.entry.point"), 
        JavaBundle.message("column.name.method.entry.point")};
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public int getRowCount() {
      return myModifiedPatterns.size();
    }

    @Override
    public @Nullable Object getValueAt(int row, int col) {
      if (row < 0 || row > myModifiedPatterns.size() - 1) return null;
      final EntryPointsManagerBase.ClassPattern classPattern = myModifiedPatterns.get(row);
      if (classPattern == null) return null;
      if (col == 0) {
        return classPattern.hierarchically;
      }
      if (col == 1) {
        return classPattern.pattern;
      }
      return classPattern.method;
    }

    @Override
    public @NlsContexts.ColumnName String getColumnName(int column) {
      return myNames[column];
    }

    @Override
    public Class<?> getColumnClass(int col) {
      if (col == 0) {
        return Boolean.class;
      }
      if (col == 1) {
        return String.class;
      }
      if (col == 2) {
        return String.class;
      }
      throw new IllegalArgumentException(String.valueOf(col));
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return true;
    }

    @Override
    public void setValueAt(Object aValue, int row, int col) {
      EntryPointsManagerBase.ClassPattern classPattern = myModifiedPatterns.get(row);
      if (classPattern == null) return;
      if (col == 0) {
        classPattern.hierarchically = (boolean)aValue;
      }
      else if (col == 1){
        classPattern.pattern = (String)aValue;
      }
      else {
        classPattern.method = (String)aValue;
      }
      fireTableRowsUpdated(row, row);
    }

    @Override
    public void removeRow(int idx) {
      myModifiedPatterns.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }
  }
}
