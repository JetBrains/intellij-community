/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

public class ListEditForm {
  JPanel contentPanel;
  ListTable table;
  private final @Nullable Function<@NotNull Project, @Nullable String> myNewElementSupplier;

  public ListEditForm(@NlsContexts.ColumnName String title, List<String> stringList) {
    table = new ListTable(new ListWrappingTableModel(stringList, title));
    myNewElementSupplier = null;
    contentPanel = setupActions(ToolbarDecorator.createDecorator(table), "").createPanel();
  }

  public ListEditForm(@NlsContexts.ColumnName String title, @NlsContexts.Label String label, List<String> stringList) {
    this(title, label, stringList, "");
  }

  public ListEditForm(@NlsContexts.ColumnName String title,
                      @NlsContexts.Label String label,
                      List<String> stringList,
                      @NotNull String defaultElement) {
    this(title, label, stringList, defaultElement, null);
  }

  public ListEditForm(@NlsContexts.ColumnName String title, @NlsContexts.Label String label, List<String> stringList, @NotNull String defaultElement,
                      @Nullable Function<@NotNull Project, @Nullable String> newElementSupplier) {
    table = new ListTable(new ListWrappingTableModel(stringList, title));
    myNewElementSupplier = newElementSupplier;
    table.setTableHeader(null);
    table.setShowHorizontalLines(false);

    contentPanel = setupActions(ToolbarDecorator.createDecorator(table), defaultElement)
      .setToolbarPosition(ActionToolbarPosition.LEFT)
      .createPanel();
    contentPanel = UI.PanelFactory.panel(contentPanel).withLabel(label).moveLabelOnTop().resizeY(true).createPanel();
    contentPanel.setMinimumSize(InspectionOptionsPanel.getMinimumListSize());
  }

  private @NotNull ToolbarDecorator setupActions(@NotNull ToolbarDecorator decorator, @NotNull String defaultElement) {
    return decorator.setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          if (myNewElementSupplier != null) {
            final DataContext dataContext = DataManager.getInstance().getDataContext(table);
            final Project project = CommonDataKeys.PROJECT.getData(dataContext);
            final int rowIndex;
            final ListWrappingTableModel tableModel = table.getModel();
            if (project != null) {
              String newElement = myNewElementSupplier.apply(project);
              if (newElement == null) return;
              final int index = tableModel.indexOf(newElement, 0);
              if (index < 0) {
                tableModel.addRow(newElement);
                rowIndex = tableModel.getRowCount() - 1;
              }
              else {
                rowIndex = index;
              }
              table.setRowSelectionInterval(rowIndex, rowIndex);
              return;
            }
          }
          final ListWrappingTableModel tableModel = table.getModel();
          tableModel.addRow(defaultElement);
          EventQueue.invokeLater(() -> {
            final int lastRowIndex = tableModel.getRowCount() - 1;
            final Rectangle rectangle =
              table.getCellRect(lastRowIndex, 0, true);
            table.scrollRectToVisible(rectangle);
            table.editCellAt(lastRowIndex, 0);
            final ListSelectionModel selectionModel =
              table.getSelectionModel();
            selectionModel.setSelectionInterval(lastRowIndex, lastRowIndex);
            final TableCellEditor editor = table.getCellEditor();
            final Component component =
              editor.getTableCellEditorComponent(table,
                                                 defaultElement, true, lastRowIndex, 0);
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
          });
        }
      })
      .setRemoveAction(button -> TableUtil.removeSelectedItems(table))
      .disableUpDownActions();
  }

  public JComponent getContentPanel() {
    return contentPanel;
  }
}
