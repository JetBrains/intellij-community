package com.intellij.database.datagrid;

import com.intellij.database.extractors.BinaryDisplayType;
import com.intellij.database.extractors.DisplayType;
import com.intellij.lang.Language;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public interface DataGridListener extends EventListener {
  Topic<DataGridListener> TOPIC = Topic.create("DATA_GRID_TOPIC", DataGridListener.class);

  default void onSelectionChanged(DataGrid dataGrid) { }

  default void onSelectionChanged(DataGrid dataGrid, boolean isAdjusting) {
    if (!isAdjusting) onSelectionChanged(dataGrid);
  }

  default void onContentChanged(DataGrid dataGrid, @Nullable GridRequestSource.RequestPlace place) { }

  default void onCellLanguageChanged(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Language language) { }

  default void onValueEdited(DataGrid dataGrid, @Nullable Object object) { }

  default void onCellDisplayTypeChanged(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType type) { }
}
