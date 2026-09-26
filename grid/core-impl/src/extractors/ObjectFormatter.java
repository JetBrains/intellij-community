package com.intellij.database.extractors;

import com.intellij.database.datagrid.GridColumn;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ObjectFormatter {
  @Nullable
  @NonNls
  String objectToString(@Nullable Object o, GridColumn column, @NotNull ObjectFormatterConfig config);

  boolean isStringLiteral(@Nullable GridColumn column, @Nullable Object value, @NotNull ObjectFormatterMode mode);

  @NotNull
  @NonNls
  String getStringLiteral(@NotNull String value, GridColumn column, @NotNull ObjectFormatterMode mode);
}
