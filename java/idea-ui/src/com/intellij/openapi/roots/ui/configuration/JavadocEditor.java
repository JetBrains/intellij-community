/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.UIUtil;

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

  private static final String NAME = ProjectBundle.message("module.javadoc.title");

  public JavadocEditor(ModuleConfigurationState state) {
    super(state);
  }

  @Override
  public String getHelpTopic() {
    return "projectStructure.modules.paths";
  }

  @Override
  public String getDisplayName() {
    return NAME;
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
          myDescriptor.setTitle(ProjectBundle.message("module.javadoc.add.path.title"));
          myDescriptor.setDescription(ProjectBundle.message("module.javadoc.add.path.prompt"));
          VirtualFile[] files = FileChooser.chooseFiles(myDescriptor, myTable, myProject, null);
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
      }).addExtraAction(new DumbAwareActionButton(ProjectBundle.message("module.javadoc.add.url.button"), IconUtil.getAddLinkIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
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
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final List removedItems = TableUtil.removeSelectedItems(myTable);
          if (removedItems.size() > 0) {
            saveData();
          }
        }
      }).setButtonComparator("Add", ProjectBundle.message("module.javadoc.add.url.button"), "Remove").createPanel();

    final JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(tablePanel, BorderLayout.CENTER);
    mainPanel.add(
      new JBLabel(ProjectBundle.message("project.roots.javadoc.tab.description"), UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER),
      BorderLayout.NORTH);
    return mainPanel;
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
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
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
    public Class getColumnClass(int columnIndex) {
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
}
