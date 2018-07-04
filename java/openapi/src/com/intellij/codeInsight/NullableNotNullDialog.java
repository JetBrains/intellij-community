// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.codeInsight;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import sun.swing.table.DefaultTableCellHeaderRenderer;

import javax.swing.*;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class NullableNotNullDialog extends DialogWrapper {
  private final Project myProject;
  private final AnnotationsPanel myNullablePanel;
  private final AnnotationsPanel myNotNullPanel;
  private final boolean myShowInstrumentationOptions;

  public NullableNotNullDialog(@NotNull Project project) {
    this(project, false);
  }

  private NullableNotNullDialog(@NotNull Project project, boolean showInstrumentationOptions) {
    super(project, true);
    myProject = project;
    myShowInstrumentationOptions = showInstrumentationOptions;

    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    myNullablePanel = new AnnotationsPanel("Nullable",
                                           manager.getDefaultNullable(),
                                           manager.getNullables(), NullableNotNullManager.DEFAULT_NULLABLES,
                                           Collections.emptySet(), false);
    myNotNullPanel = new AnnotationsPanel("NotNull",
                                          manager.getDefaultNotNull(),
                                          manager.getNotNulls(), NullableNotNullManager.DEFAULT_NOT_NULLS,
                                          ContainerUtil.newHashSet(manager.getInstrumentedNotNulls()), showInstrumentationOptions);

    init();
    setTitle("Nullable/NotNull Configuration");
  }

  public static JButton createConfigureAnnotationsButton(Component context) {
    final JButton button = new JButton(InspectionsBundle.message("configure.annotations.option"));
    button.addActionListener(createActionListener(context));
    return button;
  }

  /**
   * Creates an action listener showing this dialog.
   * @param context  component where project context will be retrieved from
   * @return the action listener
   */
  public static ActionListener createActionListener(Component context) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showDialog(context, false);
      }
    };
  }

  public static void showDialogWithInstrumentationOptions(@NotNull Component context) {
    showDialog(context, true);
  }

  private static void showDialog(Component context, boolean showInstrumentationOptions) {
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(context));
    if (project == null) project = ProjectManager.getInstance().getDefaultProject();
    NullableNotNullDialog dialog = new NullableNotNullDialog(project, showInstrumentationOptions);
    dialog.show();
  }

  @Override
  protected JComponent createCenterPanel() {
    final Splitter splitter = new Splitter(true);
    splitter.setFirstComponent(myNullablePanel.getComponent());
    splitter.setSecondComponent(myNotNullPanel.getComponent());
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setPreferredSize(JBUI.size(300, 400));
    return splitter;
  }

  @Override
  protected void doOKAction() {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);

    manager.setNotNulls(myNotNullPanel.getAnnotations());
    manager.setDefaultNotNull(myNotNullPanel.getDefaultAnnotation());

    manager.setNullables(myNullablePanel.getAnnotations());
    manager.setDefaultNullable(myNullablePanel.getDefaultAnnotation());

    if (myShowInstrumentationOptions) {
      manager.setInstrumentedNotNulls(myNotNullPanel.getCheckedAnnotations());
    }

    super.doOKAction();
  }

  private class AnnotationsPanel {
    private String myDefaultAnnotation;
    private final Set<String> myDefaultAnnotations;
    private final JBTable myTable;
    private final JPanel myComponent;
    private final DefaultTableModel myTableModel;

    private AnnotationsPanel(String name, String defaultAnnotation, List<String> annotations, String[] defaultAnnotations, Set<String> checkedAnnotations, boolean showInstrumentationOptions) {
      myDefaultAnnotation = defaultAnnotation;
      myDefaultAnnotations = new HashSet<>(Arrays.asList(defaultAnnotations));
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
        protected void customizeCellRenderer(JTable table,
                                             Object value,
                                             boolean selected,
                                             boolean hasFocus,
                                             int row,
                                             int column) {
          append((String)value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          if (value.equals(myDefaultAnnotation)) {
            setIcon(AllIcons.Diff.CurrentLine);
          } else {
            setIcon(EmptyIcon.ICON_16);
          }
        }
      }, null));
      if (showInstrumentationOptions) {
        columnModel.getColumn(0).setHeaderValue("Annotation");

        TableColumn checkColumn = new TableColumn(1, 100, new BooleanTableCellRenderer(), new BooleanTableCellEditor());
        columnModel.addColumn(checkColumn);
        checkColumn.setHeaderValue(" Instrument ");

        DefaultTableCellHeaderRenderer renderer = new DefaultTableCellHeaderRenderer();
        renderer.setToolTipText("Add runtime assertions for notnull-annotated methods and parameters");
        checkColumn.setHeaderRenderer(renderer);
        checkColumn.sizeWidthToFit();
      }

      myTable = new JBTable(myTableModel, columnModel);

      final AnActionButton selectButton =
        new AnActionButton("Select annotation used for code generation", AllIcons.Actions.Checked) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            String selectedValue = getSelectedAnnotation();
            if (selectedValue == null) return;
            myDefaultAnnotation = selectedValue;

            // to show the new default value in the ui
            myTableModel.fireTableRowsUpdated(myTable.getSelectedRow(), myTable.getSelectedRow());
          }

          @Override
          public void updateButton(AnActionEvent e) {
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
                                                                .setRemoveActionUpdater(e -> !myDefaultAnnotations.contains(getSelectedAnnotation()))
                                                                .addExtraAction(selectButton);
      final JPanel panel = toolbarDecorator.createPanel();
      myComponent = new JPanel(new BorderLayout());
      myComponent.setBorder(IdeBorderFactory.createTitledBorder(name + " annotations", false, JBUI.insetsTop(10)));
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
      return selectedRow <0 ? null : (String)myTable.getValueAt(selectedRow, 0);
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

    JComponent getComponent() {
      return myComponent;
    }

    String getDefaultAnnotation() {
      return myDefaultAnnotation;
    }

    String[] getAnnotations() {
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
}
