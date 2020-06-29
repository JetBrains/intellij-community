// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
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
    setTitle(IdeBundle.message("dialog.title.experimental.features"));
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    ExperimentalFeature[] features = Experiments.EP_NAME.getExtensions();
    JBTable table = new JBTable(createModel(features));
    table.getEmptyText().setText(IdeBundle.message("empty.text.no.features.available"));
    table.getColumnModel().getColumn(0).setCellRenderer(getIdRenderer());
    table.getColumnModel().getColumn(1).setCellRenderer(getValueRenderer());
    table.getColumnModel().getColumn(1).setCellEditor(new BooleanTableCellEditor());
    table.setStriped(true);
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    JTextArea myDescription = new JTextArea(4, 50);
    myDescription.setMargin(JBUI.insets(2));
    myDescription.setWrapStyleWord(true);
    myDescription.setLineWrap(true);
    myDescription.setEditable(false);

    table.getSelectionModel().addListSelectionListener((e) -> myDescription.setText(features[table.getSelectedRow()].description));
    final JScrollPane label = ScrollPaneFactory.createScrollPane(myDescription);
    BorderLayoutPanel descriptionPanel = JBUI.Panels.simplePanel(label)
      .withBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("border.title.description"), false));

    return JBUI.Panels.simplePanel(ScrollPaneFactory.createScrollPane(table))
      .addToBottom(descriptionPanel);
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "ExperimentsDialog";
  }

  private static TableCellRenderer getValueRenderer() {
    return new BooleanTableCellRenderer(SwingConstants.CENTER);
  }

  private static TableCellRenderer getIdRenderer() {
    return new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
        append(String.valueOf(value));
      }
    };
  }

  private static TableModel createModel(ExperimentalFeature[] experimentalFeatures) {
    return new AbstractTableModel() {
      final ExperimentalFeature[] features = experimentalFeatures;

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
          case 1: return Experiments.getInstance().isFeatureEnabled(id);
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
          case 1: return "Enabled";
          default: throw new IllegalArgumentException("Wrong column number");
        }
      }

      @Override
      public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (value instanceof Boolean) {
          Experiments.getInstance().setFeatureEnabled(features[rowIndex].id, ((Boolean)value).booleanValue());
        }
      }
    };
  }
}
