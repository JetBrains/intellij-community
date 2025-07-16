// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.builder.VerticalComponentGap;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class NullableAnnotationsPanel {
  private final Project myProject;
  private final Set<String> myDefaultAnnotations;
  private final JBTable myTable;
  private final JPanel myComponent;
  private final ComboBox<String> myCombo;
  protected final DefaultTableModel myTableModel;

  public NullableAnnotationsPanel(Project project,
                                  @NotNull AnnotationPanelModel model,
                                  boolean showInstrumentationOptions) {
    myProject = project;
    myDefaultAnnotations = new HashSet<>(model.getDefaultAnnotations());

    List<String> annotations = model.getAnnotations();
    myCombo = new ComboBox<>(annotations.stream().sorted().toArray(String[]::new));
    String defaultAnnotation = model.getDefaultAnnotation();
    if (!annotations.contains(defaultAnnotation)) {
      addAnnotationToCombo(defaultAnnotation);
    }
    if (model.hasAdvancedAnnotations()) {
      loadAdvancedAnnotations(model);
    }
    myCombo.setSelectedItem(defaultAnnotation);

    myTableModel = new DefaultTableModel() {
      @Override
      public boolean isCellEditable(int row, int column) {
        return column == 1;
      }
    };
    myTableModel.setColumnCount(showInstrumentationOptions ? 2 : 1);
    for (String annotation : annotations) {
      addRow(annotation, model.getCheckedAnnotations().contains(annotation));
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
    if (!showInstrumentationOptions) myTable.setTableHeader(null);

    if (showInstrumentationOptions) {
      columnModel.getColumn(0).setHeaderValue(JavaPsiBundle.message("node.annotation.tooltip"));

      TableColumn checkColumn = new TableColumn(1, 100, new BooleanTableCellRenderer(), new BooleanTableCellEditor());
      columnModel.addColumn(checkColumn);
      checkColumn.setHeaderValue(" Instrument ");

      TableCellRenderer headerRenderer = createHeaderRenderer();
      myTable.getTableHeader().setDefaultRenderer(headerRenderer);
      checkColumn.setHeaderRenderer(headerRenderer);
      checkColumn.sizeWidthToFit();
    }

    final ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myTable)
      .setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int selectedRow = myTable.getSelectedRow();
          if (selectedRow < 1) return;
          @SuppressWarnings("unchecked")
          List<Object> vector = myTableModel.getDataVector().get(selectedRow);
          myTableModel.removeRow(selectedRow);
          myTableModel.insertRow(selectedRow - 1, vector.toArray());
          myTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1);
        }
      })
      .setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int selectedRow = myTable.getSelectedRow();
          if (selectedRow < 0 || selectedRow >= myTableModel.getRowCount() - 1) return;
          @SuppressWarnings("unchecked") 
          List<Object> vector = myTableModel.getDataVector().get(selectedRow);
          myTableModel.removeRow(selectedRow);
          myTableModel.insertRow(selectedRow + 1, vector.toArray());
          myTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1);
        }
      })
      .setAddAction(b -> chooseAnnotation(model.getName()))
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
      .withLabel(JavaBundle.message("nullable.notnull.annotations.panel.title", model.getName()))
      .withComment(JavaBundle.message("nullable.notnull.annotations.panel.description"))
      .moveLabelOnTop()
      .resizeY(true)
      .createPanel();
    tablePanel.setPreferredSize(new JBDimension(tablePanel.getPreferredSize().width, 200));

    myComponent = new JPanel(new GridBagLayout());
    myComponent.putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH);
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.WEST;
    constraints.weightx = 1;
    myComponent.add(new JLabel(JavaBundle.message("nullable.notnull.annotation.used.label")), constraints);
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets.bottom = 3;
    constraints.gridy = 1;
    myComponent.add(myCombo, constraints);
    constraints.insets.bottom = 0;
    constraints.gridy = 2;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weighty = 1;
    myComponent.add(tablePanel, constraints);
  }

  private @NotNull TableCellRenderer createHeaderRenderer() {
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
    return headerRenderer;
  }

  private void loadAdvancedAnnotations(@NotNull AnnotationPanelModel model) {
    // No project-specific annotations are possible for default project
    if (myProject.isDefault()) return;
    String loading = JavaBundle.message("loading.additional.annotations");
    myCombo.addItem(loading);
    DumbService.getInstance(myProject).runWhenSmart(() -> {
      ReadAction.nonBlocking(model::getAdvancedAnnotations)
        .finishOnUiThread(ModalityState.any(), advancedAnnotations -> {
          myCombo.removeItem(loading);
          int count = myCombo.getItemCount();
          Object selectedItem = myCombo.getSelectedItem();
          List<String> newItems = Stream.concat(
            IntStream.range(0, count).mapToObj(myCombo::getItemAt),
            advancedAnnotations.stream()).distinct().toList();
          myCombo.removeAllItems();
          newItems.forEach(myCombo::addItem);
          myCombo.setSelectedItem(selectedItem);
        }).submit(AppExecutorUtil.getAppExecutorService());
    });
    myCombo.addActionListener(new ActionListener() {
      Object previous = myCombo.getSelectedItem();

      @Override
      public void actionPerformed(ActionEvent e) {
        Object item = myCombo.getSelectedItem();
        if (item == loading) {
          myCombo.setSelectedItem(previous);
        } else {
          previous = item;
        }
      }
    });
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
          if (!aClass.isAnnotationType()) return false;
          return true;
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