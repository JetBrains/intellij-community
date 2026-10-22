package com.intellij.database.datagrid;

import com.intellij.database.settings.DataGridAppearanceSettings;
import org.jetbrains.annotations.NotNull;

public interface DataGridAppearance {
  void setResultViewVisibleRowCount(int v);

  void setResultViewShowRowNumbers(boolean v);

  void setTransparentColumnHeaderBackground(boolean v);

  void setTransparentRowHeaderBackground(boolean v);

  void setResultViewAdditionalRowsCount(int v);

  void setResultViewSetShowHorizontalLines(boolean v);

  void setResultViewStriped(boolean v);

  void setBooleanMode(@NotNull DataGridAppearanceSettings.BooleanMode v);

  @NotNull DataGridAppearanceSettings.BooleanMode getBooleanMode();

  void setResultViewAllowMultilineColumnLabels(boolean v);

  void setAnonymousColumnName(@NotNull String name);

  void addSpaceForHorizontalScrollbar(boolean v);

  void expandMultilineRows(boolean v);

  default void setHoveredRowBgHighlightingEnabled(boolean v) {}

  /** Whether the columns share the width of the view, as a read-only preview needs, instead of keeping their own widths. */
  default void setResultViewFitColumnsToWidth(boolean v) {}
}
