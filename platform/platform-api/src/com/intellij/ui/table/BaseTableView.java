// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.table;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Do NOT add code that assumes that table has same number of rows as model. It isn't true!
 */
public class BaseTableView extends JBTable {
  private static final Logger LOG = Logger.getInstance(BaseTableView.class);

  public BaseTableView(final TableModel model) {
    super(model);
  }

  public BaseTableView(TableModel model, TableColumnModel columnModel) {
    super(model, columnModel);
  }

  @NonNls
  private static String orderPropertyName(final int index) {
    return "Order"+index;
  }

  @NonNls
  private static String widthPropertyName(int index) {
    return "Width" + index;
  }

  public static void store(@NotNull PropertiesComponent propertyComponent, @NotNull String prefix, @NotNull JTable table) {
    TableColumnModel model = table.getTableHeader().getColumnModel();
    int columnCount = model.getColumnCount();
    boolean[] storedColumns = new boolean[columnCount];
    Arrays.fill(storedColumns, false);
    for (int i = 0; i < columnCount; i++) {
      TableColumn column = model.getColumn(i);
      propertyComponent.setValue(prefix + widthPropertyName(i), String.valueOf(column.getWidth()));
      int modelIndex = column.getModelIndex();
      propertyComponent.setValue(prefix + orderPropertyName(i), String.valueOf(modelIndex));
      if (storedColumns[modelIndex]) {
        LOG.error("columnCount: " + columnCount + " current: " + i + " modelINdex: " + modelIndex);
      }
      storedColumns[modelIndex] = true;
    }
  }

  public static void storeWidth(@NotNull PropertiesComponent propertyComponent, @NotNull String prefix, @NotNull TableColumnModel columns) {
    for (int i = 0; i < columns.getColumnCount(); i++) {
      propertyComponent.setValue(prefix + widthPropertyName(i), String.valueOf(columns.getColumn(i).getWidth()));
    }
  }

  public static void storeWidth(@NotNull BiConsumer<String, String> consumer, @NotNull TableColumnModel columns) {
    for (int i = 0; i < columns.getColumnCount(); i++) {
      consumer.accept(widthPropertyName(i), String.valueOf(columns.getColumn(i).getWidth()));
    }
  }

  public static void restore(@NotNull PropertiesComponent propertyComponent, @NotNull String prefix, JTable table) {
    TableColumnModel columnModel = table.getTableHeader().getColumnModel();
    int index = 0;
    List<String> columnIndices = new ArrayList<>();
    while (true) {
      String order = propertyComponent.getValue(prefix + orderPropertyName(index));
      if (order == null) {
        break;
      }
      columnIndices.add(order);
      index++;
      if (index == table.getColumnCount()) break;
    }
    index = 0;
    for (String columnIndex : columnIndices) {
      int modelColumnIndex = indexByModelIndex(columnModel, Integer.parseInt(columnIndex));
      if (modelColumnIndex > 0 && modelColumnIndex < columnModel.getColumnCount()) {
        columnModel.moveColumn(modelColumnIndex, index);
      }
      index++;
    }
    for (int i = 0; i < columnIndices.size(); i++) {
      String width = propertyComponent.getValue(prefix + widthPropertyName(i));
      if (width != null && width.length() > 0) {
        try {
          columnModel.getColumn(i).setPreferredWidth(Integer.parseInt(width));
        }
        catch(NumberFormatException e) {
          LOG.error("Bad width: " + width + " at column: "+ i + " from: " + prefix +
                    " actual columns count: " + columnModel.getColumnCount() +
                    " info count: " + columnIndices.size(), e);
        }
      }
    }
  }

  public static void restoreWidth(@NotNull PropertiesComponent propertyComponent, @NotNull String prefix, TableColumnModel columns) {
    restoreWidth(s -> propertyComponent.getValue(prefix + s), columns);
  }

  public static void restoreWidth(@NotNull Function<String, String> map, TableColumnModel columns) {
    for (int index = 0; true; index++) {
      String widthValue = map.fun(widthPropertyName(index));
      if (widthValue == null) {
        break;
      }

      try {
        columns.getColumn(index).setPreferredWidth(Integer.parseInt(widthValue));
      }
      catch (NumberFormatException e) {
        LOG.error("Bad width: " + widthValue + " at column: " + index + " from: " + map, e);
      }
    }
  }

  private static int indexByModelIndex(TableColumnModel model, int index) {
    for (int i = 0; i < model.getColumnCount(); i++)
      if (model.getColumn(i).getModelIndex() == index)
        return i;
    LOG.error("Total: " + model.getColumnCount() + " index: " + index);
    return index;
  }
}
