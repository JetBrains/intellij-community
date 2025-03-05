package com.intellij.database.datagrid;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public final class NamedRow extends DataConsumer.Row implements GridRow {
  public final @NlsSafe String name;

  private NamedRow(int rowNum, @NotNull String name, Object @NotNull [] values) {
    super(rowNum, values);
    this.name = name;
  }

  public static NamedRow create(int realIdx, @NotNull String name, Object @NotNull [] values) {
    return new NamedRow(realIdx + 1, name, values);
  }
}
