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
package com.intellij.application;

import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/**
 * @author Konstantin Bulenkov
 */
public class ExperimentsDialog extends DialogWrapper {
  protected ExperimentsDialog(@Nullable Project project) {
    super(project);
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    ExperimentalFeature[] features = Experiments.EP_NAME.getExtensions();
    JBTable table = new JBTable(createModel(features));
    table.getEmptyText().setText("No features available");
    table.getColumnModel().getColumn(0).setCellRenderer(getIdRenderer());
    table.getColumnModel().getColumn(1).setCellRenderer(getValueRenderer());
    table.getColumnModel().getColumn(1).setCellEditor(new BooleanTableCellEditor());
    table.setStriped(true);
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JTextArea myDescription = new JTextArea(3, 50);
    myDescription.setWrapStyleWord(true);
    myDescription.setLineWrap(true);
    myDescription.setEditable(false);

    table.getSelectionModel().addListSelectionListener((e) -> myDescription.setText(features[table.getSelectedRow()].description));
    final JScrollPane label = ScrollPaneFactory.createScrollPane(myDescription);
    BorderLayoutPanel descriptionPanel = JBUI.Panels.simplePanel(label)
      .withBorder(IdeBorderFactory.createTitledBorder("Description", false));

    return JBUI.Panels.simplePanel(ScrollPaneFactory.createScrollPane(table))
      .addToBottom(descriptionPanel);
  }

  private TableCellRenderer getValueRenderer() {
    return new BooleanTableCellRenderer(SwingConstants.CENTER);
  }

  private TableCellRenderer getIdRenderer() {
    return new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
        append(String.valueOf(value));
      }
    };
  }

  private TableModel createModel(ExperimentalFeature[] experimentalFeatures) {
    return new AbstractTableModel() {
      ExperimentalFeature[] features = experimentalFeatures;

      @Override
      public int getRowCount() {
        return features.length;
      }

      @Override
      public int getColumnCount() {
        return 2;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        String id = features[rowIndex].id;
        switch (columnIndex) {
          case 0: return id;
          case 1: return Experiments.isFeatureEnabled(id);
          default: throw new IllegalArgumentException("Wrong column number");
        }
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 1;
      }

      @Override
      public String getColumnName(int column) {
        switch (column) {
          case 0: return "Name";
          case 1: return "Value";
          default: throw new IllegalArgumentException("Wrong column number");
        }
      }

      @Override
      public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (value instanceof Boolean) {
          Experiments.setFeatureEnabled(features[rowIndex].id, ((Boolean)value).booleanValue());
        }
      }
    };
  }
}
