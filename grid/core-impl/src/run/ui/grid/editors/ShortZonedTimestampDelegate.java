package com.intellij.database.run.ui.grid.editors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;

public abstract class ShortZonedTimestampDelegate<T> extends DateAndTimeFormatterDelegate<T, TemporalAccessor> {
  public ShortZonedTimestampDelegate() {
    super(LocalDateTime::from, temporal -> LocalDate.from(temporal).atTime(0, 0, 0));
  }
}
