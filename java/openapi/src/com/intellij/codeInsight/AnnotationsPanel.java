// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class AnnotationsPanel {
  private final Project myProject;
  private final Set<String> myDefaultAnnotations;
  private final JBTable myTable;
  private final JPanel myComponent;
  private final ComboBox<String> myCombo;
  protected final DefaultTableModel myTableModel;
  private final TableRowSorter<DefaultTableModel> mySorter;

  public AnnotationsPanel(Project project,
                          @NonNls String name,
                          String defaultAnnotation,
                          List<String> annotations,
                          List<String> defaultAnnotations,
                          Set<String> checkedAnnotations,
                          boolean showInstrumentationOptions,
                          boolean showDefaultActions) {
    this(project, name, defaultAnnotation, annotations, defaultAnnotations, checkedAnnotations, annotations, showInstrumentationOptions, showDefaultActions);
  }

  public AnnotationsPanel(Project project,
                          @NlsSafe String name,
                          @NlsSafe String defaultAnnotation,
                          List<String> annotations,
                          List<String> defaultAnnotations,
                          Set<String> checkedAnnotations,
                          List<String> comboAnnotations,
                          boolean showInstrumentationOptions,
                          boolean showDefaultActions) {
    myProject = project;
    myDefaultAnnotations = new HashSet<>(defaultAnnotations);

    myCombo = new ComboBox<>(comboAnnotations.stream().sorted().toArray(String[]::new));
    for (String annotation : annotations) {
      if (!comboAnnotations.contains(annotation)) addAnnotationToCombo(annotation);
    }
    if (!comboAnnotations.contains(defaultAnnotation)) addAnnotationToCombo(defaultAnnotation);
    myCombo.setSelectedItem(defaultAnnotation);

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
      }
    }, null));

    myTable = new JBTable(myTableModel, columnModel);
    mySorter = new TableRowSorter<>(myTableModel);
    mySorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
    myTable.setRowSorter(mySorter);
    if (!showInstrumentationOptions) myTable.setTableHeader(null);
    mySorter.sort();

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

    final ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myTable).disableUpDownActions()
      .setAddAction(b -> chooseAnnotation(name))
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          String selectedValue = getSelectedAnnotation();
          if (selectedValue == null) return;
          myCombo.removeItem(selectedValue);

          int rowIndex = -1;
          for (int i = 0; i < myTableModel.getDataVector().size(); i++) {
            if (myTableModel.getDataVector().get(i).contains(selectedValue)) {
              rowIndex = i;
              break;
            }
          }
          if (rowIndex != -1) myTableModel.removeRow(rowIndex);
        }
      })
      .setRemoveActionUpdater(e -> !myDefaultAnnotations.contains(getSelectedAnnotation()));

    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setRowSelectionAllowed(true);
    myTable.setShowGrid(false);

    final var tablePanel = UI.PanelFactory
      .panel(toolbarDecorator.createPanel())
      .withLabel(JavaBundle.message("nullable.notnull.annotations.panel.title", name))
      .moveLabelOnTop()
      .resizeY(true)
      .createPanel();
    tablePanel.setPreferredSize(new JBDimension(tablePanel.getPreferredSize().width, 200));

    myComponent = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridy = 0;
    if (showDefaultActions) {
      myComponent.add(new JLabel(JavaBundle.message("nullable.notnull.annotation.used.label")), constraints);
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.weightx = 1;
      myComponent.add(myCombo, constraints);

      constraints.gridy = 1;
      constraints.gridwidth = 2;
    }
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1;
    constraints.weighty = 1;
    myComponent.add(tablePanel, constraints);
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

  private @NlsSafe String getSelectedAnnotation() {
    int selectedRow = myTable.getSelectedRow();
    return selectedRow < 0 ? null : (String)myTable.getValueAt(selectedRow, 0);
  }

  private void chooseAnnotation(@NlsSafe String title) {
    final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
      .createNoInnerClassesScopeChooser(JavaBundle.message("dialog.title.choose.annotation", title), GlobalSearchScope.allScope(myProject), new ClassFilter() {
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
      addAnnotationToCombo(qualifiedName);
      mySorter.sort();
      Object added = selectAnnotation(qualifiedName);
      assert added != null;
      myTable.scrollRectToVisible(myTable.getCellRect((int)added, 0, true));
    }
  }

  private void addAnnotationToCombo(@NlsSafe String annotation) {
    int insertAt = 0;
    for (; insertAt < myCombo.getItemCount(); insertAt += 1) {
      if (myCombo.getItemAt(insertAt).compareTo(annotation) >= 0) break;
    }
    myCombo.insertItemAt(annotation, insertAt);
  }

  public JComponent getComponent() {
    return myComponent;
  }

  String getDefaultAnnotation() {
    return myCombo.getItem();
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