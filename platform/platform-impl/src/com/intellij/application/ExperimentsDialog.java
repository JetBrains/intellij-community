// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class ExperimentsDialog extends DialogWrapper {
  ExperimentsDialog(@Nullable Project project) {
    super(project);
    init();
    setTitle(IdeBundle.message("dialog.title.experimental.features"));
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    ExperimentalFeature[] features = Experiments.EP_NAME.getExtensions();
    JBTable table = new JBTable(createModel(features));
    table.setShowGrid(false);
    table.getEmptyText().setText(IdeBundle.message("empty.text.no.features.available"));
    table.getColumnModel().getColumn(0).setCellRenderer(getIdRenderer());
    table.getColumnModel().getColumn(1).setCellRenderer(getValueRenderer());
    table.getColumnModel().getColumn(1).setCellEditor(new BooleanTableCellEditor());
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

  @Override
  protected @NotNull String getDimensionServiceKey() {
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
        return switch (columnIndex) {
          case 0 -> id;
          case 1 -> Experiments.getInstance().isFeatureEnabled(id);
          default -> throw new IllegalArgumentException("Wrong column number");
        };
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 1;
      }

      @Override
      public String getColumnName(int column) {
        return switch (column) {
          case 0 -> ApplicationBundle.message("column.name");
          case 1 -> IdeBundle.message("column.enabled");
          default -> throw new IllegalArgumentException("Wrong column number");
        };
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
