package com.intellij.database.run.ui.grid.editors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public abstract class ZonedTimestampDelegate<T> extends DateAndTimeFormatterDelegate<T, OffsetDateTime> {
  public ZonedTimestampDelegate() {
    super(OffsetDateTime::from,
          temporal -> ZonedDateTime.from(temporal).toOffsetDateTime(),
          temporal -> LocalDateTime.from(temporal).atOffset(DataGridFormattersUtilCore.getDefaultOffset()),
          temporal -> LocalDate.from(temporal).atTime(0, 0, 0).atOffset(DataGridFormattersUtilCore.getDefaultOffset()));
  }
}
