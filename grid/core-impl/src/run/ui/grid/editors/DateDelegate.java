package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.extractors.FormatterCreator;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.Date;

public class DateDelegate extends DateAndTimeFormatterDelegate<Date, LocalDate> {
  private final FormatsCache myFormatsCache;
  private final FormatterCreator myFormatterCreator;

  public DateDelegate(@NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
    super(LocalDate::from);
    myFormatsCache = formatsCache;
    myFormatterCreator = formatterCreator;
  }

  @Override
  protected Date createFromTemporal(@NotNull LocalDate value) {
    return DataGridFormattersUtilCore.fromLocalDate(value, myFormatsCache, myFormatterCreator);
  }

  @Override
  protected LocalDate toTemporalAccessor(@NotNull Object value) {
    if (!(value instanceof Date)) throw new IllegalArgumentException("Value must be of type Date");
    return DataGridFormattersUtilCore.fromDate((Date)value, myFormatsCache, myFormatterCreator);
  }
}
