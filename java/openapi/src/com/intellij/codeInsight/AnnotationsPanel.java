// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.core.JavaPsiBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AnnotationsPanel {
  private final Project myProject;
  private String myDefaultAnnotation;
  private final Set<String> myDefaultAnnotations;
  private final JBTable myTable;
  private final JPanel myComponent;
  protected final DefaultTableModel myTableModel;

  public AnnotationsPanel(Project project,
                          String name,
                          String defaultAnnotation,
                          List<String> annotations,
                          List<String> defaultAnnotations,
                          Set<String> checkedAnnotations,
                          boolean showInstrumentationOptions,
                          boolean showDefaultActions) {
    myProject = project;
    myDefaultAnnotation = defaultAnnotation;
    myDefaultAnnotations = new HashSet<>(defaultAnnotations);
    myTableModel = new DefaultTableModel() {
      @Override
      public boolean isCellEditable(int row, int column) {
        return column == 1;
      }
    };
    myTableModel.setColumnCount(showInstrumentationOptions ? 2 : 1);
    for (String annotation : annotations) {
      addRow(annotation, checkedAnnotations.contains(annotation));
    }

    DefaultTableColumnModel columnModel = new DefaultTableColumnModel();
    columnModel.addColumn(new TableColumn(0, 100, new ColoredTableCellRenderer() {
      @Override
      public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
        super.acquireState(table, isSelected, false, row, column);
      }

      @Override
      protected void customizeCellRenderer(@NotNull JTable table,
                                           Object value,
                                           boolean selected,
                                           boolean hasFocus,
                                           int row,
                                           int column) {
        append((String)value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (value.equals(myDefaultAnnotation)) {
          setIcon(AllIcons.Actions.Forward);
        }
        else {
          setIcon(EmptyIcon.ICON_16);
        }
      }
    }, null));

    myTable = new JBTable(myTableModel, columnModel);

    if (showInstrumentationOptions) {
      columnModel.getColumn(0).setHeaderValue(JavaPsiBundle.message("node.annotation.tooltip"));

      TableColumn checkColumn = new TableColumn(1, 100, new BooleanTableCellRenderer(), new BooleanTableCellEditor());
      columnModel.addColumn(checkColumn);
      checkColumn.setHeaderValue(" Instrument ");

      TableCellRenderer defaultRenderer = myTable.getTableHeader().getDefaultRenderer();

      TableCellRenderer headerRenderer = new TableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Component component = defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (component instanceof JComponent) {
            ((JComponent)component)
              .setToolTipText(column == 1 ? JavaBundle.message("nullable.notnull.annotations.runtime.instrumentation.tooltip") : null);
          }
          return component;
        }
      };
      myTable.getTableHeader().setDefaultRenderer(headerRenderer);
      checkColumn.setHeaderRenderer(headerRenderer);
      checkColumn.sizeWidthToFit();
    }

    final AnActionButton selectButton =
      new AnActionButton(JavaBundle.messagePointer("action.AnActionButton.text.select.annotation.used.for.code.generation"),
                         AllIcons.Actions.Checked) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String selectedValue = getSelectedAnnotation();
          if (selectedValue == null) return;
          myDefaultAnnotation = selectedValue;

          // to show the new default value in the ui
          myTableModel.fireTableRowsUpdated(myTable.getSelectedRow(), myTable.getSelectedRow());
        }

        @Override
        public void updateButton(@NotNull AnActionEvent e) {
          String selectedValue = getSelectedAnnotation();
          e.getPresentation().setEnabled(selectedValue != null && !selectedValue.equals(myDefaultAnnotation));
        }
      };

    final ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myTable).disableUpDownActions()
      .setAddAction(b -> chooseAnnotation(name))
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          String selectedValue = getSelectedAnnotation();
          if (selectedValue == null) return;
          if (myDefaultAnnotation.equals(selectedValue)) myDefaultAnnotation = (String)myTable.getValueAt(0, 0);

          myTableModel.removeRow(myTable.getSelectedRow());
        }
      })
      .setRemoveActionUpdater(e -> !myDefaultAnnotations.contains(getSelectedAnnotation()));
    if (showDefaultActions) {
      toolbarDecorator.addExtraAction(selectButton);
    }
    final JPanel panel = toolbarDecorator.createPanel();
    myComponent = new JPanel(new BorderLayout());
    myComponent.setBorder(IdeBorderFactory.createTitledBorder(JavaBundle.message("nullable.notnull.annotations.panel.title", name), false, JBUI.insetsTop(10)));
    myComponent.add(panel);
    myComponent.setPreferredSize(new JBDimension(myComponent.getPreferredSize().width, 200));

    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setRowSelectionAllowed(true);
    myTable.setShowGrid(false);

    selectAnnotation(myDefaultAnnotation);
  }

  private void addRow(String annotation, boolean checked) {
    myTableModel.addRow(new Object[]{annotation, checked});
  }

  private Integer selectAnnotation(String annotation) {
    for (int i = 0; i < myTable.getRowCount(); i++) {
      if (annotation.equals(myTable.getValueAt(i, 0))) {
        myTable.setRowSelectionInterval(i, i);
        return i;
      }
    }
    return null;
  }

  private String getSelectedAnnotation() {
    int selectedRow = myTable.getSelectedRow();
    return selectedRow < 0 ? null : (String)myTable.getValueAt(selectedRow, 0);
  }

  private void chooseAnnotation(String title) {
    final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
      .createNoInnerClassesScopeChooser("Choose " + title + " annotation", GlobalSearchScope.allScope(myProject), new ClassFilter() {
        @Override
        public boolean isAccepted(PsiClass aClass) {
          return aClass.isAnnotationType();
        }
      }, null);
    chooser.showDialog();
    final PsiClass selected = chooser.getSelected();
    if (selected == null) {
      return;
    }
    final String qualifiedName = selected.getQualifiedName();
    if (selectAnnotation(qualifiedName) == null) {
      addRow(qualifiedName, false);
    }
  }

  public JComponent getComponent() {
    return myComponent;
  }

  String getDefaultAnnotation() {
    return myDefaultAnnotation;
  }

  public String[] getAnnotations() {
    int size = myTable.getRowCount();
    String[] result = new String[size];
    for (int i = 0; i < size; i++) {
      result[i] = (String)myTable.getValueAt(i, 0);
    }
    return result;
  }

  List<String> getCheckedAnnotations() {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < myTable.getRowCount(); i++) {
      if (Boolean.TRUE.equals(myTable.getValueAt(i, 1))) {
        result.add((String)myTable.getValueAt(i, 0));
      }
    }
    return result;
  }
}