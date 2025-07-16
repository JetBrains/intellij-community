// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

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
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.builder.VerticalComponentGap;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                          List<String> annotations,
                          List<String> defaultAnnotations) {
    myProject = project;
    myDefaultAnnotations = new HashSet<>(defaultAnnotations);

    myCombo = new ComboBox<>(annotations.stream().sorted().toArray(String[]::new));
    if (!annotations.contains("")) {
      addAnnotationToCombo("");
    }
    myCombo.setSelectedItem("");

    myTableModel = new DefaultTableModel() {
      @Override
      public boolean isCellEditable(int row, int column) {
        return column == 1;
      }
    };
    myTableModel.setColumnCount(1);
    for (String annotation : annotations) {
      addRow(annotation);
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
    myTable.setTableHeader(null);
    mySorter.sort();

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
      .withLabel(JavaBundle.message("annotations.panel.title", name))
      .moveLabelOnTop()
      .resizeY(true)
      .createPanel();
    tablePanel.setPreferredSize(new JBDimension(tablePanel.getPreferredSize().width, 200));

    myComponent = new JPanel(new GridBagLayout());
    myComponent.putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH);
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.WEST;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weighty = 1;
    myComponent.add(tablePanel, constraints);
  }

  protected boolean isAnnotationAccepted(PsiClass annotation) {
    return true;
  }

  private void addRow(String annotation) {
    myTableModel.addRow(new Object[]{annotation});
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
          return aClass.isAnnotationType() && isAnnotationAccepted(aClass);
        }
      }, null);
    chooser.showDialog();
    final PsiClass selected = chooser.getSelected();
    if (selected == null) {
      return;
    }
    final String qualifiedName = selected.getQualifiedName();
    if (selectAnnotation(qualifiedName) == null) {
      addRow(qualifiedName);
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

  public String[] getAnnotations() {
    int size = myTable.getRowCount();
    String[] result = new String[size];
    for (int i = 0; i < size; i++) {
      result[i] = (String)myTable.getValueAt(i, 0);
    }
    return result;
  }

  /** Reset table to contain only annotations from the list. */
  public void resetAnnotations(List<String> annotations) {
    final Set<String> set = new HashSet<>(annotations);
    int row = 0;
    for (String annotation : getAnnotations()) {
      if (!set.contains(annotation)) {
        myTableModel.removeRow(row);
      } else {
        set.remove(annotation);
        row++;
      }
    }
    for (String annotation : set) {
      addRow(annotation);
    }
  }
}