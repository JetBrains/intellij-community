package com.intellij.database.csv;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CsvRecord {
  public final TextRange range;
  public final List<ValueRange> values;
  public final boolean hasRecordSeparator;

  public CsvRecord(@NotNull TextRange range,
            @NotNull List<ValueRange> values,
            boolean hasRecordSeparator) {
    this.range = range;
    this.values = values;
    this.hasRecordSeparator = hasRecordSeparator;
  }
}
