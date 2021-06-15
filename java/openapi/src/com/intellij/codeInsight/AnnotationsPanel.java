// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.core.JavaPsiBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class AnnotationsPanel {
  private final Project myProject;
  private String myDefaultAnnotation;
  private final Set<String> myDefaultAnnotations;
  private final JBTable myTable;
  private final JPanel myComponent;
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
          setIcon(AllIcons.Actions.SetDefault);
          append(" ");
          append(JavaBundle.message("annotations.panel.used.for.code.generation"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          setIcon(EmptyIcon.ICON_16);
        }
      }
    }, null));

    myTable = new JBTable(myTableModel, columnModel);
    mySorter = new TableRowSorter<>(myTableModel);
    mySorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.DESCENDING)));
    myTable.setRowSorter(mySorter);
    if (!showInstrumentationOptions) myTable.setTableHeader(null);
    mySorter.sort();

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
        int row = myTable.getSelectedRow();
        String annotation = (String) myTable.getValueAt(row, 0);
        if (annotation != null) {
          myDefaultAnnotation = annotation;
          myTableModel.fireTableRowsUpdated(row, row);
        }
        return true;
      }
    }.installOn(myTable);

    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new AnAction(JavaBundle.message("annotations.panel.use.for.code.generation")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            myDefaultAnnotation = getSelectedAnnotation();
          }
        });
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
        popupMenu.getComponent().show(myTable, x, y);
      }
    });

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
                         AllIcons.Actions.SetDefault) {
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
    if (showDefaultActions) {
      toolbarDecorator.addExtraAction(selectButton);
    }
    myComponent = UI.PanelFactory
      .panel(toolbarDecorator.createPanel())
      .withLabel(JavaBundle.message("nullable.notnull.annotations.panel.title", name))
      .moveLabelOnTop()
      .resizeY(true)
      .createPanel();
    myComponent.setPreferredSize(new JBDimension(myComponent.getPreferredSize().width, 200));

    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setRowSelectionAllowed(true);
    myTable.setShowGrid(false);
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
      mySorter.sort();
      Object added = selectAnnotation(qualifiedName);
      assert added != null;
      myTable.scrollRectToVisible(myTable.getCellRect((int)added, 0, true));
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