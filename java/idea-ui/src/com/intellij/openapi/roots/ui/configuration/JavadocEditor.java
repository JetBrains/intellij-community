// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.CommonBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ItemRemovable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class JavadocEditor extends ModuleElementsEditor {
  private JTable myTable;

  public JavadocEditor(ModuleConfigurationState state) {
    super(state);
  }

  @Override
  public String getHelpTopic() {
    return "projectStructure.modules.paths";
  }

  @Override
  public @NlsContexts.ConfigurableName String getDisplayName() {
    return getName();
  }

  @Override
  public void saveData() {
    TableUtil.stopEditing(myTable);
    final int count = myTable.getRowCount();
    String[] urls = ArrayUtil.newStringArray(count);
    for (int row = 0; row < count; row++) {
      final TableItem item = ((MyTableModel)myTable.getModel()).getTableItemAt(row);
      urls[row] = item.getUrl();
    }
    getModel().getModuleExtension(JavaModuleExternalPaths.class).setJavadocUrls(urls);
    fireConfigurationChanged();
  }

  @Override
  public JComponent createComponentImpl() {
    final DefaultTableModel tableModel = createModel();
    myTable = new JBTable(tableModel);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setDefaultRenderer(TableItem.class, new MyRenderer());
    myTable.setShowGrid(false);
    myTable.setDragEnabled(false);
    myTable.setShowHorizontalLines(false);
    myTable.setShowVerticalLines(false);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    JPanel tablePanel = ToolbarDecorator.createDecorator(myTable)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          FileChooserDescriptor myDescriptor = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor();
          myDescriptor.setTitle(JavaUiBundle.message("module.javadoc.add.path.title"));
          myDescriptor.setDescription(JavaUiBundle.message("module.javadoc.add.path.prompt"));
          VirtualFile[] files = FileChooser.chooseFiles(myDescriptor, myTable, getProject(), null);
          final MyTableModel tableModel = (MyTableModel)myTable.getModel();
          boolean changes = false;
          for (final VirtualFile file : files) {
            if (file != null) {
              tableModel.addTableItem(new TableItem(file));
              changes = true;
            }
          }
          if (changes) {
            saveData();
            TableUtil.selectRows(myTable, new int[]{tableModel.getRowCount() - 1});
          }
        }
      })
      .addExtraAction(new DumbAwareAction(JavaUiBundle.messagePointer("module.javadoc.add.url.button"), IconUtil.getAddLinkIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          VirtualFile[] files = new VirtualFile[]{Util.showSpecifyJavadocUrlDialog(myTable)};
          final MyTableModel tableModel = (MyTableModel)myTable.getModel();
          boolean changes = false;
          for (final VirtualFile file : files) {
            if (file != null) {
              tableModel.addTableItem(new TableItem(file));
              changes = true;
            }
          }
          if (changes) {
            saveData();
            TableUtil.selectRows(myTable, new int[]{tableModel.getRowCount() - 1});
          }
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final List<Object[]> removedItems = TableUtil.removeSelectedItems(myTable);
          if (!removedItems.isEmpty()) {
            saveData();
          }
        }
      })
      .setButtonComparator(CommonBundle.message("button.add"), JavaUiBundle.message("module.javadoc.add.url.button"),
                             CommonBundle.message("button.remove")).createPanel();

    return new JavadocEditorUi().createPanel(tablePanel);
  }

  private @NotNull Project getProject() {
    return myProject;
  }

  protected DefaultTableModel createModel() {
    final MyTableModel tableModel = new MyTableModel();
    final String[] javadocUrls = getModel().getModuleExtension(JavaModuleExternalPaths.class).getJavadocUrls();
    for (String javadocUrl : javadocUrls) {
      tableModel.addTableItem(new TableItem(javadocUrl));
    }
    return tableModel;
  }

  @Override
  public void moduleStateChanged() {
    if (myTable != null) {
      final DefaultTableModel tableModel = createModel();
      myTable.setModel(tableModel);
    }
  }

  private static class MyRenderer extends ColoredTableCellRenderer {
    private static final Border NO_FOCUS_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    @Override
    protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setPaintFocusBorder(false);
      setFocusBorderAroundIcon(true);
      setBorder(NO_FOCUS_BORDER);

      final TableItem tableItem = ((TableItem)value);
      if (tableItem != null) {
        tableItem.getCellAppearance().customize(this);
      }
    }
  }

  private static class MyTableModel extends DefaultTableModel implements ItemRemovable {
    @Override
    public String getColumnName(int column) {
      return null;
    }

    @Override
    public Class<TableItem> getColumnClass(int columnIndex) {
      return TableItem.class;
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }

    public TableItem getTableItemAt(int row) {
      return (TableItem)getValueAt(row, 0);
    }

    public void addTableItem(TableItem item) {
      addRow(new Object[]{item});
    }
  }

  private static @NlsContexts.ConfigurableName String getName() {
    return JavaUiBundle.message("module.javadoc.title");
  }
}
